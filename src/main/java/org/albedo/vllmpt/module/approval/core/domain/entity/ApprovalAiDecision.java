package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 决策审计快照 approval_ai_decision。
 * <p>
 * 每次 AI 决策（包括转人工、异常降级）都落一条，用于解释“为什么批/为什么不批”。
 */
@Data
@TableName("approval_ai_decision")
public class ApprovalAiDecision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String nodeKey;

    /** 同一实例同一节点的决策序号，从 1 开始 */
    private Integer decisionNo;

    private String policyKey;

    /** APPROVE / REJECT / ESCALATE */
    private String decision;

    private BigDecimal confidence;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    private String factsSnapshot;

    private String ruleResult;

    private String llmOutput;

    private String modelName;

    private String promptVersion;

    private String policyVersion;

    private Integer latencyMs;

    private LocalDateTime createdAt;
}
