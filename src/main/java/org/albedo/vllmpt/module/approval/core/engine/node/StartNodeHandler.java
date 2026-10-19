package org.albedo.vllmpt.module.approval.core.engine.node;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.springframework.stereotype.Component;

/**
 * 开始节点：只做入口标记。
 */
@Component
public class StartNodeHandler implements NodeHandler {

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.START;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        return NodeResult.pass();
    }
}
