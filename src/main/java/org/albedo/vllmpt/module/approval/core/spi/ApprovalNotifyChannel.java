package org.albedo.vllmpt.module.approval.core.spi;

/**
 * 审批通知渠道插件。
 */
public interface ApprovalNotifyChannel {

    String channel();

    void send(ApprovalNotifyMessage message);
}
