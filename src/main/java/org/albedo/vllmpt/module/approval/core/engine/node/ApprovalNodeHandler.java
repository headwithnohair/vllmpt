package org.albedo.vllmpt.module.approval.core.engine.node;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAction;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalMode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalOperatorType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTaskStatus;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTaskType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.albedo.vllmpt.module.approval.core.engine.assignee.AssigneeResolverRegistry;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工审批节点：解析审批人并生成任务。
 * <p>
 * 任务状态规则：
 * <ul>
 *   <li>ANY：全部任务 PENDING，任意一人通过即推进；</li>
 *   <li>ALL：全部任务 PENDING，全部通过才推进；</li>
 *   <li>SEQUENTIAL：首个 PENDING，其余 WAITING，逐个激活。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalNodeHandler implements NodeHandler {

    private final AssigneeResolverRegistry resolverRegistry;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalRecordMapper recordMapper;

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.APPROVAL;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        ApprovalInstance instance = ctx.instance();
        List<Long> resolved = resolverRegistry.resolve(
                node.getAssigneeType(), node.getAssigneeConfig(), instance, ctx.facts());
        if (resolved.isEmpty()) {
            throw new BusinessException(400,
                    "节点[" + node.getNodeName() + "]未解析到审批人，请检查审批人配置或员工档案");
        }

        // 审批人就是申请人时自动跳过，避免自审
        List<Long> assignees = resolved.stream()
                .filter(id -> !id.equals(instance.getApplicantId()))
                .toList();
        if (assignees.isEmpty()) {
            saveSkipRecord(instance, node);
            log.info("[审批] 审批人即申请人，节点自动跳过: instanceId={}, node={}",
                    instance.getId(), node.getNodeKey());
            return NodeResult.pass();
        }

        ApprovalMode mode = parseMode(node.getApprovalMode());
        for (int i = 0; i < assignees.size(); i++) {
            taskMapper.insert(buildTask(instance, node, mode, assignees.get(i), i + 1));
        }
        log.info("[审批] 节点生成待办: instanceId={}, node={}, mode={}, assignees={}",
                instance.getId(), node.getNodeKey(), mode, assignees);
        return NodeResult.waitNode();
    }

    private ApprovalTask buildTask(ApprovalInstance instance, ApprovalTemplateNode node,
                                   ApprovalMode mode, Long assigneeId, int sequenceNo) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalTask task = new ApprovalTask();
        task.setTaskKey(instance.getId() + ":" + node.getNodeKey() + ":" + assigneeId + ":" + sequenceNo);
        task.setInstanceId(instance.getId());
        task.setNodeKey(node.getNodeKey());
        task.setNodeName(node.getNodeName());
        task.setAssigneeId(assigneeId);
        task.setAssigneeType(node.getAssigneeType());
        task.setTaskType(ApprovalTaskType.APPROVAL.name());
        task.setApprovalMode(mode.name());
        task.setSequenceNo(sequenceNo);
        task.setStatus(mode == ApprovalMode.SEQUENTIAL && sequenceNo > 1
                ? ApprovalTaskStatus.WAITING.name()
                : ApprovalTaskStatus.PENDING.name());
        task.setVersion(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private void saveSkipRecord(ApprovalInstance instance, ApprovalTemplateNode node) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setNodeKey(node.getNodeKey());
        record.setOperatorType(ApprovalOperatorType.SYSTEM.name());
        record.setAction(ApprovalAction.SKIP.name());
        record.setComment("审批人即申请人，自动跳过");
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private ApprovalMode parseMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return ApprovalMode.ANY;
        }
        try {
            return ApprovalMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的审批模式: " + mode);
        }
    }
}
