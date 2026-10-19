package org.albedo.vllmpt.module.approval.core.domain.model;

/**
 * 节点处理结果类型。
 */
public enum NodeResultType {
    /** 节点已生成待办，流程在此等待 */
    WAIT,
    /** 节点处理完成，继续下一节点 */
    PASS,
    /** 拒绝，实例结束为 REJECTED */
    REJECT,
    /** 流程正常结束 */
    COMPLETE
}
