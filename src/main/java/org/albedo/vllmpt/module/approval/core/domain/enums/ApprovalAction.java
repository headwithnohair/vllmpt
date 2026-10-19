package org.albedo.vllmpt.module.approval.core.domain.enums;

/**
 * 审批动作，对应 approval_record.action。
 */
public enum ApprovalAction {
    SUBMIT,
    APPROVE,
    REJECT,
    TRANSFER,
    ADD_SIGN,
    WITHDRAW,
    RESUBMIT,
    CC,
    TERMINATE,
    /** 节点无有效审批人（如审批人就是申请人）时自动跳过 */
    SKIP,
    /** AI 决策节点主动转人工 */
    AI_ESCALATE
}
