package org.albedo.vllmpt.module.approval.core.engine.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.springframework.stereotype.Component;

/**
 * AI 决策节点（P0 安全占位）。
 * <p>
 * AI 决策插件尚未接入时，<b>一律转人工</b>，绝不自动通过。
 * P3 接入 {@code ApprovalAiDecisionProvider} 后，在命中硬规则/高置信度时才跳到
 * {@code aiOnApproveNodeKey}，否则跳 {@code aiOnEscalateNodeKey}。
 */
@Component
@Slf4j
public class AiDecisionNodeHandler implements NodeHandler {

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.AI_DECISION;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        String escalateNodeKey = node.getAiOnEscalateNodeKey();
        log.info("[审批] AI 决策未接入，按安全策略转人工: instanceId={}, node={}, escalateNode={}",
                ctx.instance().getId(), node.getNodeKey(), escalateNodeKey);
        return StrUtil.isBlank(escalateNodeKey) ? NodeResult.pass() : NodeResult.passTo(escalateNodeKey);
    }
}
