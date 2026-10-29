package org.albedo.vllmpt.module.approval.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyChannel;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyMessage;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyRegistry;
import org.springframework.stereotype.Service;

/**
 * 审批通知服务。
 * <p>
 * 通知失败只记录日志，不影响审批事务；INBOX 落库，外部渠道由插件扩展。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalNotifyService {

    private static final String DEFAULT_CHANNEL = "INBOX";

    private final ApprovalNotifyRegistry channelRegistry;

    public void sendCc(ApprovalInstance instance, ApprovalTemplateNode node, Long receiverId) {
        String notifyKey = "CC:" + instance.getId() + ":" + node.getNodeKey() + ":" + receiverId;
        ApprovalNotifyMessage message = new ApprovalNotifyMessage(
                notifyKey,
                instance.getId(),
                null,
                receiverId,
                "CC",
                DEFAULT_CHANNEL,
                "抄送提醒：" + instance.getTitle(),
                "审批单【" + instance.getTitle() + "】已抄送给你，单号：" + instance.getInstanceNo());
        ApprovalNotifyChannel channel = channelRegistry.find(DEFAULT_CHANNEL);
        if (channel == null) {
            log.warn("[审批] 未注册站内信渠道，跳过通知: {}", notifyKey);
            return;
        }
        try {
            channel.send(message);
        } catch (Exception e) {
            log.error("[审批] 抄送通知发送失败，不影响审批: key={}", notifyKey, e);
        }
    }
}
