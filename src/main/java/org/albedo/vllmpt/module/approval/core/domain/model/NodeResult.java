package org.albedo.vllmpt.module.approval.core.domain.model;

/**
 * 节点处理结果。
 * <p>
 * {@code nextNodeKey} 为空表示按 sort_order 走下一个节点；
 * 非空表示显式跳转（AI 决策节点通过它跳过主管或转人工）。
 */
public record NodeResult(NodeResultType type, String nextNodeKey, String comment) {

    public static NodeResult waitNode() {
        return new NodeResult(NodeResultType.WAIT, null, null);
    }

    public static NodeResult pass() {
        return new NodeResult(NodeResultType.PASS, null, null);
    }

    public static NodeResult passTo(String nextNodeKey) {
        return new NodeResult(NodeResultType.PASS, nextNodeKey, null);
    }

    public static NodeResult reject(String comment) {
        return new NodeResult(NodeResultType.REJECT, null, comment);
    }

    public static NodeResult complete() {
        return new NodeResult(NodeResultType.COMPLETE, null, null);
    }
}
