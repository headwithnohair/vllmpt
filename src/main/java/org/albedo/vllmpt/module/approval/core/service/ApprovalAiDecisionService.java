package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalAiDecision;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAction;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalOperatorType;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalAiDecisionMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.spi.AiDecision;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * AI 决策审计：写 approval_ai_decision 快照 + approval_record 流水。
 * <p>
 * 审计失败会抛出异常，由审批事务回滚，避免“批了但无法解释”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalAiDecisionService {

    private final ApprovalAiDecisionMapper decisionMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final ApprovalAiProperties properties;
    private final ApprovalAiMetrics metrics;

    public void save(ApprovalInstance instance, ApprovalTemplateNode node,
                     AiDecisionResult result, long latencyMs) {
        int decisionNo = nextDecisionNo(instance.getId(), node.getNodeKey());

        ApprovalAiDecision decision = new ApprovalAiDecision();
        decision.setInstanceId(instance.getId());
        decision.setNodeKey(node.getNodeKey());
        decision.setDecisionNo(decisionNo);
        decision.setPolicyKey(node.getAiPolicyKey());
        decision.setDecision(result.decision().name());
        decision.setConfidence(BigDecimal.valueOf(result.confidence())
                .setScale(3, RoundingMode.HALF_UP));
        decision.setRiskLevel(result.riskLevel() == null ? null : result.riskLevel().name());
        decision.setFactsSnapshot(StrUtil.blankToDefault(instance.getFacts(), "{}"));
        decision.setRuleResult(writeJson(result.ruleHits()));
        decision.setLlmOutput(writeJson(result.llmOutput()));
        decision.setModelName(result.modelName());
        decision.setPromptVersion(properties.getPromptVersion());
        decision.setPolicyVersion(result.policyVersion());
        decision.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
        decision.setCreatedAt(LocalDateTime.now());
        decisionMapper.insert(decision);

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setNodeKey(node.getNodeKey());
        record.setOperatorType(ApprovalOperatorType.AI.name());
        record.setAction(action(result.decision()).name());
        record.setComment(StrUtil.maxLength(result.reason(), 1000));
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        metrics.record(result.decision(), latencyMs);

        log.info("[审批AI] 审计完成: instanceId={}, node={}, decision={}, confidence={}, latency={}ms",
                instance.getId(), node.getNodeKey(), result.decision(),
                result.confidence(), latencyMs);
    }

    private int nextDecisionNo(Long instanceId, String nodeKey) {
        Long count = decisionMapper.selectCount(Wrappers.<ApprovalAiDecision>lambdaQuery()
                .eq(ApprovalAiDecision::getInstanceId, instanceId)
                .eq(ApprovalAiDecision::getNodeKey, nodeKey));
        return count == null ? 1 : count.intValue() + 1;
    }

    private ApprovalAction action(AiDecision decision) {
        return switch (decision) {
            case APPROVE -> ApprovalAction.AI_APPROVE;
            case REJECT -> ApprovalAction.AI_REJECT;
            case ESCALATE -> ApprovalAction.AI_ESCALATE;
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[审批AI] 审计 JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
