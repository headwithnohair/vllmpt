package org.albedo.vllmpt.module.approval.core.domain.enums;

/**
 * 审批任务类型。
 */
public enum ApprovalTaskType {
    /** 待人工审批 */
    APPROVAL,
    /** 抄送（不阻塞流程） */
    CC
}
