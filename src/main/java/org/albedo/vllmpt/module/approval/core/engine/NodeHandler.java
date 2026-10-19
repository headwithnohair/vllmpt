package org.albedo.vllmpt.module.approval.core.engine;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;

/**
 * 流程节点处理器。
 * <p>
 * 新增节点类型时只需要新增一个实现，不需要改 {@code ApprovalEngine}。
 */
public interface NodeHandler {

    ApprovalNodeType supportType();

    NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node);
}
