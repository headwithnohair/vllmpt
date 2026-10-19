package org.albedo.vllmpt.module.approval.core.domain.enums;

/**
 * 审批任务状态。
 */
public enum ApprovalTaskStatus {
    /** 顺序审批中尚未轮到的任务 */
    WAITING,
    /** 当前可处理 */
    PENDING,
    APPROVED,
    REJECTED,
    SKIPPED,
    TRANSFERRED,
    CANCELED
}
