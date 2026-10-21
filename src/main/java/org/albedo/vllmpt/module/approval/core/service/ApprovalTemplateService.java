package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateCreateRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateNodeRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateUpdateRequest;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalMode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTemplateStatus;
import org.albedo.vllmpt.module.approval.core.engine.assignee.AssigneeResolverRegistry;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 审批模板服务。
 * <p>
 * 模板发布后不再修改；同一个 templateKey 再次创建即为新版本，
 * 运行中的实例固定引用旧版本，不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTemplateService {

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalTemplateNodeMapper nodeMapper;
    private final AssigneeResolverRegistry assigneeResolverRegistry;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long create(TemplateCreateRequest request) {
        ApprovalTemplate latest = latestByKey(request.getTemplateKey());
        if (latest != null && ApprovalTemplateStatus.DRAFT.name().equals(latest.getStatus())) {
            throw new BusinessException(400, "该 templateKey 已有草稿版本，请先发布");
        }
        int version = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;

        ApprovalTemplate template = new ApprovalTemplate();
        template.setTemplateKey(request.getTemplateKey().trim());
        template.setBizType(request.getBizType().trim().toUpperCase());
        template.setName(request.getName().trim());
        template.setVersion(version);
        template.setStatus(ApprovalTemplateStatus.DRAFT.name());
        template.setFormSchema(writeJson(request.getFormSchema()));
        template.setDescription(request.getDescription());
        template.setAiFillEnabled(Boolean.TRUE.equals(request.getAiFillEnabled()) ? 1 : 0);
        template.setCreatedBy(UserContextHolder.getUserId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);

        saveNodes(template.getId(), request.getNodes());

        log.info("[审批] 创建模板: key={}, version={}, templateId={}",
                template.getTemplateKey(), version, template.getId());
        return template.getId();
    }

    @Transactional
    public void publish(Long templateId) {
        ApprovalTemplate template = requireTemplate(templateId);
        if (!ApprovalTemplateStatus.DRAFT.name().equals(template.getStatus())) {
            throw new BusinessException(400, "只有草稿模板可以发布");
        }
        List<ApprovalTemplateNode> nodes = listNodes(templateId);
        validateForPublish(nodes);

        template.setStatus(ApprovalTemplateStatus.PUBLISHED.name());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        log.info("[审批] 发布模板: key={}, version={}, templateId={}",
                template.getTemplateKey(), template.getVersion(), templateId);
    }

    public TemplateDetailVO detail(Long templateId) {
        return new TemplateDetailVO(requireTemplate(templateId), listNodes(templateId));
    }

    /**
     * 编辑草稿模板：只允许 DRAFT，节点全量替换。
     */
    @Transactional
    public void update(Long templateId, TemplateUpdateRequest request) {
        ApprovalTemplate template = requireTemplate(templateId);
        if (!ApprovalTemplateStatus.DRAFT.name().equals(template.getStatus())) {
            throw new BusinessException(400, "只有草稿模板可以编辑");
        }

        template.setName(request.getName().trim());
        template.setDescription(request.getDescription());
        template.setFormSchema(writeJson(request.getFormSchema()));
        if (request.getAiFillEnabled() != null) {
            template.setAiFillEnabled(Boolean.TRUE.equals(request.getAiFillEnabled()) ? 1 : 0);
        }
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);

        nodeMapper.delete(Wrappers.<ApprovalTemplateNode>lambdaQuery()
                .eq(ApprovalTemplateNode::getTemplateId, templateId));
        saveNodes(templateId, request.getNodes());
        log.info("[审批] 编辑草稿模板: templateId={}, key={}, version={}",
                templateId, template.getTemplateKey(), template.getVersion());
    }

    /**
     * 下线已发布模板：只影响新实例，运行中的实例固定引用原 templateId。
     */
    @Transactional
    public void disable(Long templateId) {
        ApprovalTemplate template = requireTemplate(templateId);
        if (!ApprovalTemplateStatus.PUBLISHED.name().equals(template.getStatus())) {
            throw new BusinessException(400, "只有已发布模板可以下线");
        }
        template.setStatus(ApprovalTemplateStatus.DISABLED.name());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        log.info("[审批] 下线模板: templateId={}, key={}, version={}",
                templateId, template.getTemplateKey(), template.getVersion());
    }

    /**
     * 按业务类型返回最新已发布模板的表单 Schema，供 AI 填表与前端动态渲染使用。
     */
    public String formSchema(String bizType) {
        ApprovalTemplate template = findPublishedByBizType(bizType);
        if (template == null) {
            throw new BusinessException(404, "未找到已发布的审批模板: " + bizType);
        }
        return template.getFormSchema();
    }

    public List<ApprovalTemplate> list(String bizType) {
        return templateMapper.selectList(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(StrUtil.isNotBlank(bizType), ApprovalTemplate::getBizType,
                        bizType == null ? null : bizType.trim().toUpperCase())
                .orderByDesc(ApprovalTemplate::getId)
                .last("LIMIT 100"));
    }

    public ApprovalTemplate findPublishedByKey(String templateKey) {
        return templateMapper.selectOne(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(ApprovalTemplate::getTemplateKey, templateKey.trim())
                .eq(ApprovalTemplate::getStatus, ApprovalTemplateStatus.PUBLISHED.name())
                .orderByDesc(ApprovalTemplate::getVersion)
                .last("LIMIT 1"));
    }

    public ApprovalTemplate findPublishedByBizType(String bizType) {
        return templateMapper.selectOne(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(ApprovalTemplate::getBizType, bizType.trim().toUpperCase())
                .eq(ApprovalTemplate::getStatus, ApprovalTemplateStatus.PUBLISHED.name())
                .orderByDesc(ApprovalTemplate::getVersion)
                .last("LIMIT 1"));
    }

    private ApprovalTemplate latestByKey(String templateKey) {
        return templateMapper.selectOne(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(ApprovalTemplate::getTemplateKey, templateKey.trim())
                .orderByDesc(ApprovalTemplate::getVersion)
                .last("LIMIT 1"));
    }

    private ApprovalTemplate requireTemplate(Long templateId) {
        ApprovalTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(404, "审批模板不存在: " + templateId);
        }
        return template;
    }

    private List<ApprovalTemplateNode> listNodes(Long templateId) {
        return nodeMapper.selectList(Wrappers.<ApprovalTemplateNode>lambdaQuery()
                .eq(ApprovalTemplateNode::getTemplateId, templateId)
                .orderByAsc(ApprovalTemplateNode::getSortOrder)
                .orderByAsc(ApprovalTemplateNode::getId));
    }

    private void saveNodes(Long templateId, List<TemplateNodeRequest> nodeRequests) {
        Set<String> nodeKeys = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (int i = 0; i < nodeRequests.size(); i++) {
            TemplateNodeRequest nodeRequest = nodeRequests.get(i);
            String nodeKey = nodeRequest.getNodeKey().trim();
            if (!nodeKeys.add(nodeKey)) {
                throw new BusinessException(400, "节点 nodeKey 重复: " + nodeKey);
            }
            int sortOrder = nodeRequest.getSortOrder() == null ? i + 1 : nodeRequest.getSortOrder();
            if (!sortOrders.add(sortOrder)) {
                throw new BusinessException(400, "节点 sortOrder 重复: " + sortOrder);
            }
            nodeMapper.insert(toNode(templateId, nodeRequest, sortOrder));
        }
    }

    private ApprovalTemplateNode toNode(Long templateId, TemplateNodeRequest request, int sortOrder) {
        ApprovalTemplateNode node = new ApprovalTemplateNode();
        node.setTemplateId(templateId);
        node.setNodeKey(request.getNodeKey().trim());
        node.setNodeName(request.getNodeName().trim());
        node.setNodeType(request.getNodeType().trim().toUpperCase());
        node.setSortOrder(sortOrder);
        node.setAssigneeType(StrUtil.isBlank(request.getAssigneeType())
                ? null : request.getAssigneeType().trim().toUpperCase());
        node.setAssigneeConfig(writeJson(request.getAssigneeConfig()));
        node.setApprovalMode(StrUtil.isBlank(request.getApprovalMode())
                ? null : request.getApprovalMode().trim().toUpperCase());
        node.setConditionJson(writeJson(request.getConditionJson()));
        node.setAiPolicyKey(request.getAiPolicyKey());
        node.setAiMinConfidence(request.getAiMinConfidence());
        node.setAiOnApproveNodeKey(request.getAiOnApproveNodeKey());
        node.setAiOnEscalateNodeKey(request.getAiOnEscalateNodeKey());
        node.setAiOnRejectAction(request.getAiOnRejectAction());
        node.setCcConfig(writeJson(request.getCcConfig()));
        node.setSlaHours(request.getSlaHours());
        node.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        return node;
    }

    private void validateForPublish(List<ApprovalTemplateNode> nodes) {
        if (nodes.isEmpty()) {
            throw new BusinessException(400, "模板没有可发布的节点");
        }
        Set<String> nodeKeys = new HashSet<>();
        nodes.forEach(node -> nodeKeys.add(node.getNodeKey()));

        long startCount = 0;
        long endCount = 0;
        for (ApprovalTemplateNode node : nodes) {
            ApprovalNodeType type = parseNodeType(node.getNodeType());
            if (type == ApprovalNodeType.START) {
                startCount++;
            }
            if (type == ApprovalNodeType.END) {
                endCount++;
            }
            if (StrUtil.isNotBlank(node.getConditionJson())) {
                parseJson(node.getConditionJson(), "节点条件");
            }

            switch (type) {
                case APPROVAL -> {
                    if (StrUtil.isBlank(node.getAssigneeType())) {
                        throw new BusinessException(400, "审批节点必须配置 assigneeType: " + node.getNodeKey());
                    }
                    parseAssigneeType(node.getAssigneeType());
                    parseMode(node.getApprovalMode());
                    validateAssigneeConfig(node);
                }
                case AI_DECISION -> {
                    validateNodeRef(nodeKeys, node.getAiOnApproveNodeKey(), node.getNodeKey());
                    validateNodeRef(nodeKeys, node.getAiOnEscalateNodeKey(), node.getNodeKey());
                }
                case CC -> {
                    if (StrUtil.isNotBlank(node.getCcConfig())) {
                        parseJson(node.getCcConfig(), "抄送配置");
                        assigneeResolverRegistry.parseCcSpecs(node.getCcConfig());
                    }
                }
                default -> {
                    // START / CONDITION / END 无额外校验
                }
            }
        }
        if (startCount != 1) {
            throw new BusinessException(400, "模板必须且只能有一个 START 节点");
        }
        if (endCount < 1) {
            throw new BusinessException(400, "模板至少需要一个 END 节点");
        }
    }

    private void validateAssigneeConfig(ApprovalTemplateNode node) {
        ApprovalAssigneeType type = parseAssigneeType(node.getAssigneeType());
        boolean configRequired = type == ApprovalAssigneeType.USER
                || type == ApprovalAssigneeType.ROLE
                || type == ApprovalAssigneeType.FORM_FIELD;
        if (configRequired && StrUtil.isBlank(node.getAssigneeConfig())) {
            throw new BusinessException(400, "节点审批人缺少配置: " + node.getNodeKey());
        }
        if (StrUtil.isNotBlank(node.getAssigneeConfig())) {
            parseJson(node.getAssigneeConfig(), "审批人配置");
        }
    }

    private void validateNodeRef(Set<String> nodeKeys, String nodeKey, String ownerNodeKey) {
        if (StrUtil.isNotBlank(nodeKey) && !nodeKeys.contains(nodeKey)) {
            throw new BusinessException(400,
                    "节点[" + ownerNodeKey + "]跳转目标不存在: " + nodeKey);
        }
    }

    private ApprovalNodeType parseNodeType(String nodeType) {
        try {
            return ApprovalNodeType.valueOf(nodeType.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException(400, "不支持的节点类型: " + nodeType);
        }
    }

    private ApprovalAssigneeType parseAssigneeType(String assigneeType) {
        try {
            return ApprovalAssigneeType.valueOf(assigneeType.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException(400, "不支持的审批人类型: " + assigneeType);
        }
    }

    private ApprovalMode parseMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return ApprovalMode.ANY;
        }
        try {
            return ApprovalMode.valueOf(mode.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException(400, "不支持的审批模式: " + mode);
        }
    }

    private void parseJson(String json, String label) {
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, label + "不是合法 JSON");
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "JSON 序列化失败");
        }
    }
}
