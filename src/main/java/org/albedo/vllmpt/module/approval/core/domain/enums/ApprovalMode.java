package org.albedo.vllmpt.module.approval.core.domain.enums;

/**
 * 多人审批模式。
 */
public enum ApprovalMode {
    /** 或签：一人通过即节点通过 */
    ANY,
    /** 会签：全部通过才节点通过 */
    ALL,
    /** 顺序审批：按顺序逐个生成待办 */
    SEQUENTIAL
}
