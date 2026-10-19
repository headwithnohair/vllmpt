package org.albedo.vllmpt.module.approval.core.engine;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTaskStatus;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.condition.JsonConditionEvaluator;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 审批流程引擎。
 * <p>
 * P0 使用「线性节点 + 条件跳过」：节点按 sort_order 顺序推进，
 * 每个节点可通过 condition_json 判断是否跳过，AI 决策节点可通过 nextNodeKey 显式跳转。
 * 引擎不感知具体业务，业务差异由模板与节点处理器承担。
 */
@Slf4j
@Service
public class ApprovalEngine {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTemplateNodeMapper nodeMapper;
    private final ApprovalTaskMapper taskMapper;
    private final JsonConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;
    private final Map<ApprovalNodeType, NodeHandler> handlerMap;

    public ApprovalEngine(ApprovalInstanceMapper instanceMapper,
                          ApprovalTemplateNodeMapper nodeMapper,
                          ApprovalTaskMapper taskMapper,
                          JsonConditionEvaluator conditionEvaluator,
                          ObjectMapper objectMapper,
                          List<NodeHandler> handlers) {
        this.instanceMapper = instanceMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(NodeHandler::supportType, handler -> handler,
                        (left, right) -> left, () -> new EnumMap<>(ApprovalNodeType.class)));
    }

    /**
     * 从 START 节点开始执行。必须在提交事务内调用。
     */
    @Transactional
    public void start(Long instanceId) {
        ApprovalInstance instance = requireInstance(instanceId);
        if (!ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            log.info("[审批] 实例不在运行中，忽略启动: instanceId={}, status={}", instanceId, instance.getStatus());
            return;
        }
        if (StrUtil.isNotBlank(instance.getCurrentNodeKey())) {
            log.info("[审批] 实例已启动，忽略重复启动: instanceId={}", instanceId);
            return;
        }

        List<ApprovalTemplateNode> nodes = loadNodes(instance.getTemplateId());
        ApprovalTemplateNode startNode = nodes.stream()
                .filter(node -> ApprovalNodeType.START == parseNodeType(node))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "审批模板缺少 START 节点"));
        run(instance, nodes, startNode, parseFacts(instance.getFacts()));
    }

    /**
     * 从指定节点之后继续推进。人工审批任务完成后调用。
     */
    @Transactional
    public void advance(Long instanceId, String fromNodeKey) {
        ApprovalInstance instance = requireInstance(instanceId);
        if (!ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            log.info("[审批] 实例已结束，跳过推进: instanceId={}, status={}", instanceId, instance.getStatus());
            return;
        }

        List<ApprovalTemplateNode> nodes = loadNodes(instance.getTemplateId());
        ApprovalTemplateNode fromNode = nodes.stream()
                .filter(node -> node.getNodeKey().equals(fromNodeKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "流程节点不存在: " + fromNodeKey));
        run(instance, nodes, next(nodes, fromNode), parseFacts(instance.getFacts()));
    }

    /**
     * 结束为拒绝。用于会签节点出现拒绝后的收口。
     */
    @Transactional
    public void reject(Long instanceId, String nodeKey, String comment) {
        ApprovalInstance instance = requireInstance(instanceId);
        if (!ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            return;
        }
        ApprovalTemplateNode node = loadNodes(instance.getTemplateId()).stream()
                .filter(item -> item.getNodeKey().equals(nodeKey))
                .findFirst()
                .orElse(null);
        finish(instance, ApprovalInstanceStatus.REJECTED, node, comment);
    }

    private void run(ApprovalInstance instance, List<ApprovalTemplateNode> nodes,
                     ApprovalTemplateNode node, Map<String, Object> facts) {
        Set<String> visited = new LinkedHashSet<>();
        while (node != null) {
            if (!visited.add(node.getNodeKey())) {
                throw new BusinessException(500, "审批流程存在环，请检查模板节点配置: " + node.getNodeKey());
            }
            if (!conditionEvaluator.matches(node.getConditionJson(), facts)) {
                log.debug("[审批] 节点条件不满足，跳过: instanceId={}, node={}",
                        instance.getId(), node.getNodeKey());
                node = next(nodes, node);
                continue;
            }

            NodeHandler handler = handlerMap.get(parseNodeType(node));
            if (handler == null) {
                throw new BusinessException(500, "节点[" + node.getNodeKey() + "]没有处理器: " + node.getNodeType());
            }
            NodeResult result = handler.handle(new NodeHandleContext(instance, nodes, facts), node);
            if (result == null) {
                throw new BusinessException(500, "节点处理器返回空结果: " + node.getNodeKey());
            }

            switch (result.type()) {
                case WAIT -> {
                    markRunning(instance, node);
                    return;
                }
                case PASS -> node = StrUtil.isNotBlank(result.nextNodeKey())
                        ? nodeByKey(nodes, result.nextNodeKey())
                        : next(nodes, node);
                case REJECT -> {
                    finish(instance, ApprovalInstanceStatus.REJECTED, node, result.comment());
                    return;
                }
                case COMPLETE -> {
                    finish(instance, ApprovalInstanceStatus.APPROVED, node, result.comment());
                    return;
                }
            }
        }
        finish(instance, ApprovalInstanceStatus.APPROVED, null, null);
    }

    private void markRunning(ApprovalInstance instance, ApprovalTemplateNode node) {
        updateInstance(instance, wrapper -> wrapper
                .set(ApprovalInstance::getStatus, ApprovalInstanceStatus.RUNNING.name())
                .set(ApprovalInstance::getCurrentNodeKey, node.getNodeKey())
                .set(ApprovalInstance::getCurrentNodeName, node.getNodeName()));
    }

    private void finish(ApprovalInstance instance, ApprovalInstanceStatus status,
                        ApprovalTemplateNode node, String comment) {
        LocalDateTime now = LocalDateTime.now();
        String nodeKey = node == null ? null : node.getNodeKey();
        String nodeName = node == null ? null : node.getNodeName();
        updateInstance(instance, wrapper -> wrapper
                .set(ApprovalInstance::getStatus, status.name())
                .set(ApprovalInstance::getCurrentNodeKey, nodeKey)
                .set(ApprovalInstance::getCurrentNodeName, nodeName)
                .set(ApprovalInstance::getFinishedAt, now));
        cancelOpenTasks(instance.getId(), now);
        log.info("[审批] 流程结束: instanceId={}, status={}, node={}, comment={}",
                instance.getId(), status, nodeKey, comment);
    }

    private void cancelOpenTasks(Long instanceId, LocalDateTime now) {
        taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .in(ApprovalTask::getStatus,
                        ApprovalTaskStatus.PENDING.name(), ApprovalTaskStatus.WAITING.name())
                .set(ApprovalTask::getStatus, ApprovalTaskStatus.CANCELED.name())
                .set(ApprovalTask::getUpdatedAt, now));
    }

    private void updateInstance(ApprovalInstance instance,
                                Consumer<LambdaUpdateWrapper<ApprovalInstance>> customizer) {
        int version = instance.getVersion() == null ? 0 : instance.getVersion();
        LambdaUpdateWrapper<ApprovalInstance> wrapper = new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, instance.getId())
                .eq(ApprovalInstance::getVersion, version);
        customizer.accept(wrapper);
        wrapper.set(ApprovalInstance::getVersion, version + 1)
                .set(ApprovalInstance::getUpdatedAt, LocalDateTime.now());

        int rows = instanceMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(409, "审批实例已被其他操作更新，请刷新后重试");
        }
        instance.setVersion(version + 1);
    }

    private ApprovalInstance requireInstance(Long instanceId) {
        ApprovalInstance instance = instanceMapper.selectByIdForUpdate(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "审批实例不存在: " + instanceId);
        }
        return instance;
    }

    private List<ApprovalTemplateNode> loadNodes(Long templateId) {
        return nodeMapper.selectList(Wrappers.<ApprovalTemplateNode>lambdaQuery()
                .eq(ApprovalTemplateNode::getTemplateId, templateId)
                .eq(ApprovalTemplateNode::getEnabled, 1)
                .orderByAsc(ApprovalTemplateNode::getSortOrder)
                .orderByAsc(ApprovalTemplateNode::getId));
    }

    private ApprovalTemplateNode next(List<ApprovalTemplateNode> nodes, ApprovalTemplateNode current) {
        int sortOrder = current.getSortOrder() == null ? 0 : current.getSortOrder();
        return nodes.stream()
                .filter(node -> node.getSortOrder() != null && node.getSortOrder() > sortOrder)
                .findFirst()
                .orElse(null);
    }

    private ApprovalTemplateNode nodeByKey(List<ApprovalTemplateNode> nodes, String nodeKey) {
        return nodes.stream()
                .filter(node -> node.getNodeKey().equals(nodeKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "流程节点不存在: " + nodeKey));
    }

    private ApprovalNodeType parseNodeType(ApprovalTemplateNode node) {
        try {
            return ApprovalNodeType.valueOf(node.getNodeType().trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException(500, "不支持的节点类型: " + node.getNodeType());
        }
    }

    private Map<String, Object> parseFacts(String factsJson) {
        if (StrUtil.isBlank(factsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(factsJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(500, "审批事实 JSON 解析失败");
        }
    }
}
