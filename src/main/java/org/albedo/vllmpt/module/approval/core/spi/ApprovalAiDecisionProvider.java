package org.albedo.vllmpt.module.approval.core.spi;

/**
 * AI 审批决策插件。
 * <p>
 * 按模板节点的 {@code ai_policy_key} 注册；未注册时节点一律转人工。
 */
public interface ApprovalAiDecisionProvider {

    String policyKey();

    AiDecisionResult decide(AiDecisionContext ctx);

    default String policyVersion() {
        return "v1";
    }
}
