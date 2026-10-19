package org.albedo.vllmpt.module.approval.core.domain.model;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;

import java.util.List;
import java.util.Map;

/**
 * 节点处理上下文。
 *
 * @param instance 审批实例
 * @param nodes    当前模板的全部节点，按 sort_order 升序
 * @param facts    扁平事实，用于条件判断与审批人解析
 */
public record NodeHandleContext(
        ApprovalInstance instance,
        List<ApprovalTemplateNode> nodes,
        Map<String, Object> facts) {
}
