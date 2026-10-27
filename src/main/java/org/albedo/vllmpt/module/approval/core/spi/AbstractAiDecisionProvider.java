package org.albedo.vllmpt.module.approval.core.spi;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * AI 决策复用基类：硬规则优先，LLM 只处理灰区，任何异常都转人工。
 * <p>
 * 裁决顺序：
 * <ol>
 *   <li>任一硬规则 {@link RuleEffect#REJECT} → 拒绝；</li>
 *   <li>任一硬规则 {@link RuleEffect#ESCALATE} → 转人工；</li>
 *   <li>调用 LLM 风险评估；失败/高风险/低置信度 → 转人工；</li>
 *   <li>全部满足 → 自动通过。</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractAiDecisionProvider implements ApprovalAiDecisionProvider {

    /**
     * 硬规则：确定性判断，不调用模型。
     */
    protected abstract List<RuleHit> hardRules(AiDecisionContext ctx);

    /**
     * 灰区风险评估；实现方必须自行兜底异常，返回 {@link LlmAssessment#failed}。
     */
    protected abstract LlmAssessment assess(AiDecisionContext ctx, List<RuleHit> rules);

    @Override
    public AiDecisionResult decide(AiDecisionContext ctx) {
        List<RuleHit> rules;
        try {
            rules = hardRules(ctx);
            if (rules == null) {
                rules = List.of();
            }
        } catch (Exception e) {
            log.error("[审批AI] 硬规则执行失败，转人工: policyKey={}", policyKey(), e);
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "硬规则执行失败，已转人工", List.of(), Map.of(), null, policyVersion());
        }

        List<String> rejects = rules.stream().filter(RuleHit::reject).map(RuleHit::message).toList();
        if (!rejects.isEmpty()) {
            return AiDecisionResult.reject(1.0, RiskLevel.HIGH,
                    String.join("；", rejects), rules, policyVersion());
        }

        List<String> escalates = rules.stream().filter(RuleHit::escalate).map(RuleHit::message).toList();
        if (!escalates.isEmpty()) {
            return AiDecisionResult.escalate(1.0, RiskLevel.HIGH,
                    String.join("；", escalates), rules, Map.of(), null, policyVersion());
        }

        LlmAssessment llm;
        try {
            llm = assess(ctx, rules);
        } catch (Exception e) {
            log.error("[审批AI] LLM 评估异常，转人工: policyKey={}", policyKey(), e);
            llm = LlmAssessment.failed("模型评估异常：" + e.getMessage(), null);
        }
        if (llm == null || llm.failed()) {
            String reason = llm == null ? "模型评估无结果" : llm.reason();
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    reason, rules, llm == null ? Map.of() : llm.raw(),
                    llm == null ? null : llm.modelName(), policyVersion());
        }
        if (llm.risk() == RiskLevel.HIGH || llm.confidence() < ctx.minConfidence()) {
            return AiDecisionResult.escalate(llm.confidence(), llm.risk(),
                    "风险等级或置信度不满足自动通过条件：" + llm.reason(),
                    rules, llm.raw(), llm.modelName(), policyVersion());
        }
        return AiDecisionResult.approve(llm.confidence(), llm.risk(), llm.reason(),
                rules, llm.raw(), llm.modelName(), policyVersion());
    }
}
