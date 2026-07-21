package org.albedo.vllmpt.module.chat.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PipelineContext {
    private final String modelId;          // 如 "gpt-4o", "qwen-max"
    private final int maxContextTokens;    // 模型上限，如 128000
    private int reservedOutputTokens;      // 预留给生成的 token，如 4096
    private int currentTokenCount;         // 实时 token 计数

    // 预算分配（由 BudgetStage 计算后写入）
    private int systemBudget;
    private int ragBudget;
    private int historyBudget;

    // 会话元数据
    private final String sessionId;
    private final Map<String, Object> attributes;

    // 审计日志
    private final List<StageAudit> auditLog = new ArrayList<>();

    public int availableTokens() {
        return maxContextTokens - reservedOutputTokens - currentTokenCount;
    }
}