package org.albedo.vllmpt.module.approval.core.engine.node;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.springframework.stereotype.Component;

/**
 * 结束节点：流程正常完成。
 */
@Component
public class EndNodeHandler implements NodeHandler {

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.END;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        return NodeResult.complete();
    }
}
