package org.albedo.vllmpt.module.approval.core.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalNotificationMapper;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyChannel;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 站内信通知渠道：写 approval_notification，幂等键保证重复节点不重复通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxNotifyChannel implements ApprovalNotifyChannel {

    public static final String CHANNEL = "INBOX";

    private final ApprovalNotificationMapper notificationMapper;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void send(ApprovalNotifyMessage message) {
        LocalDateTime now = LocalDateTime.now();
        int rows = notificationMapper.insertIgnore(
                message.notifyKey(), message.instanceId(), message.taskId(),
                message.receiverId(), message.type(), CHANNEL,
                message.title(), message.content(), "SENT", now, now);
        if (rows > 0) {
            log.info("[审批] 站内信已写入: key={}, receiver={}", message.notifyKey(), message.receiverId());
        }
    }
}
