package org.albedo.vllmpt.module.approval.core.engine.node;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.springframework.stereotype.Component;

/**
 * 条件节点：条件已在进入节点前求值，命中即继续，未命中会被引擎跳过。
 */
@Component
public class ConditionNodeHandler implements NodeHandler {

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.CONDITION;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        return NodeResult.pass();
    }
}
