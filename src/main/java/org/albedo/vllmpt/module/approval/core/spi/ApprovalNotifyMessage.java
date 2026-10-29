package org.albedo.vllmpt.module.approval.core.spi;

/**
 * 审批通知消息。
 */
public record ApprovalNotifyMessage(String notifyKey,
                                    Long instanceId,
                                    Long taskId,
                                    Long receiverId,
                                    String type,
                                    String channel,
                                    String title,
                                    String content) {
}
