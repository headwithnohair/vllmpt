package org.albedo.vllmpt.module.approval.core.spi;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 决策上下文。
 *
 * @param instance      审批实例
 * @param node          AI_DECISION 节点
 * @param facts         扁平事实
 * @param minConfidence 本次节点要求的最低置信度
 */
public record AiDecisionContext(ApprovalInstance instance,
                                ApprovalTemplateNode node,
                                Map<String, Object> facts,
                                double minConfidence) {

    public AiDecisionContext {
        facts = facts == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
