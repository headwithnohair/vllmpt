package org.albedo.vllmpt.module.approval.core.spi;

import java.util.List;
import java.util.Map;

/**
 * AI 决策结果。
 *
 * @param decision      最终裁决
 * @param confidence    置信度
 * @param riskLevel     风险等级
 * @param reason        裁决说明
 * @param ruleHits      规则命中
 * @param llmOutput     LLM 原始输出
 * @param modelName     模型名
 * @param policyVersion 策略版本
 */
public record AiDecisionResult(AiDecision decision,
                               double confidence,
                               RiskLevel riskLevel,
                               String reason,
                               List<RuleHit> ruleHits,
                               Map<String, Object> llmOutput,
                               String modelName,
                               String policyVersion) {

    public AiDecisionResult {
        ruleHits = ruleHits == null ? List.of() : List.copyOf(ruleHits);
        llmOutput = llmOutput == null ? Map.of() : Map.copyOf(llmOutput);
    }

    public static AiDecisionResult reject(double confidence, RiskLevel risk, String reason,
                                          List<RuleHit> ruleHits, String policyVersion) {
        return new AiDecisionResult(AiDecision.REJECT, confidence, risk, reason,
                ruleHits, Map.of(), null, policyVersion);
    }

    public static AiDecisionResult escalate(double confidence, RiskLevel risk, String reason,
                                            List<RuleHit> ruleHits, Map<String, Object> llmOutput,
                                            String modelName, String policyVersion) {
        return new AiDecisionResult(AiDecision.ESCALATE, confidence, risk, reason,
                ruleHits, llmOutput, modelName, policyVersion);
    }

    public static AiDecisionResult approve(double confidence, RiskLevel risk, String reason,
                                           List<RuleHit> ruleHits, Map<String, Object> llmOutput,
                                           String modelName, String policyVersion) {
        return new AiDecisionResult(AiDecision.APPROVE, confidence, risk, reason,
                ruleHits, llmOutput, modelName, policyVersion);
    }

    /**
     * 安全降级：任何异常/低置信度/高风险都转人工。
     */
    public AiDecisionResult toEscalate(String reason) {
        return new AiDecisionResult(AiDecision.ESCALATE, confidence, RiskLevel.HIGH, reason,
                ruleHits, llmOutput, modelName, policyVersion);
    }
}
