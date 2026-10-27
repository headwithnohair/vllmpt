package org.albedo.vllmpt.module.approval.core.spi;

import java.util.Map;

/**
 * LLM 风险评估结果。
 *
 * @param failed    是否失败（超时、非法 JSON、模型不可用等）
 * @param risk      风险等级
 * @param confidence 置信度 0~1
 * @param reason    说明
 * @param raw       模型原始输出
 * @param modelName 实际使用的模型
 */
public record LlmAssessment(boolean failed,
                            RiskLevel risk,
                            double confidence,
                            String reason,
                            Map<String, Object> raw,
                            String modelName) {

    public LlmAssessment {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public static LlmAssessment failed(String reason, String modelName) {
        return new LlmAssessment(true, RiskLevel.HIGH, 0.0, reason, Map.of(), modelName);
    }

    public static LlmAssessment of(RiskLevel risk, double confidence, String reason,
                                   Map<String, Object> raw, String modelName) {
        return new LlmAssessment(false, risk, confidence, reason, raw, modelName);
    }
}
