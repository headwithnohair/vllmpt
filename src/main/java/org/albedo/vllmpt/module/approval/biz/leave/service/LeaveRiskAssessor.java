package org.albedo.vllmpt.module.approval.biz.leave.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.albedo.vllmpt.module.approval.biz.leave.domain.model.LeavePolicy;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.spi.LlmAssessment;
import org.albedo.vllmpt.module.approval.core.spi.RiskLevel;
import org.albedo.vllmpt.module.approval.core.spi.RuleHit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假灰区风险评估器：只处理硬规则通过后的灰区，模型输出严格 JSON。
 * <p>
 * 模型超时、不可用、返回非法 JSON、置信度缺失都返回 failed，由基类转人工。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRiskAssessor {

    private final ChatModelFactory chatModelFactory;
    private final ApprovalAiProperties properties;
    private final ObjectMapper objectMapper;

    public LlmAssessment assess(Map<String, Object> facts, LeavePolicy policy, List<RuleHit> rules) {
        if (!properties.isEnabled()) {
            return LlmAssessment.failed("审批 AI 决策未启用", properties.getModelName());
        }
        try {
            String prompt = buildPrompt(facts, policy, rules);
            ChatModel model = chatModelFactory.createModel(
                    properties.getModelName(), properties.getTemperature(),
                    properties.getMaxTokens(), properties.getTimeoutSeconds());
            ChatRequest request = ChatRequest.builder()
                    .messages(List.<ChatMessage>of(UserMessage.from(prompt)))
                    .build();
            ChatResponse response = model.chat(request);
            String text = response.aiMessage() == null ? null : response.aiMessage().text();
            Map<String, Object> raw = parseJson(text);
            RiskLevel risk = parseRisk(raw.get("risk"));
            double confidence = parseConfidence(raw.get("confidence"));
            String reason = String.valueOf(raw.getOrDefault("reason", ""));
            log.info("[请假AI] 风险评估: risk={}, confidence={}, reason={}", risk, confidence, reason);
            return LlmAssessment.of(risk, confidence, reason, raw, properties.getModelName());
        } catch (Exception e) {
            log.warn("[请假AI] 风险评估失败，转人工: {}", e.getMessage());
            return LlmAssessment.failed("模型评估失败：" + e.getMessage(), properties.getModelName());
        }
    }

    private String buildPrompt(Map<String, Object> facts, LeavePolicy policy, List<RuleHit> rules) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facts", facts);
        payload.put("policy", policy);
        payload.put("ruleHits", rules);
        String payloadJson = objectMapper.writeValueAsString(payload);
        return """
                你是企业请假审批的风险评估器。只评估风险，不改变硬规则结论。
                仅输出 JSON 对象，不要 Markdown、不要解释文字：
                {"risk":"LOW|MEDIUM|HIGH","confidence":0.0,"reason":"简要原因"}

                判断标准：
                - LOW：事实完整、无冲突、理由合理可解释；
                - MEDIUM：信息基本完整但理由模糊，或存在轻微异常；
                - HIGH：理由可疑、事实矛盾、需要人工核实。
                confidence 取 0~1；无法判断时输出 0 并说明原因。

                输入：
                %s
                """.formatted(payloadJson);
    }

    private Map<String, Object> parseJson(String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("模型返回为空");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型未返回 JSON 对象");
        }
        return objectMapper.readValue(text.substring(start, end + 1),
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
    }

    private RiskLevel parseRisk(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少 risk");
        }
        return RiskLevel.valueOf(String.valueOf(value).trim().toUpperCase());
    }

    private double parseConfidence(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少 confidence");
        }
        double confidence;
        if (value instanceof Number number) {
            confidence = number.doubleValue();
        } else {
            confidence = Double.parseDouble(String.valueOf(value));
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence 必须在 0~1 之间");
        }
        return confidence;
    }
}
