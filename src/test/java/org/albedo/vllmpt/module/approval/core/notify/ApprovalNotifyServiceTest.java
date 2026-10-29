package org.albedo.vllmpt.module.approval.core.notify;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalNotificationMapper;
import org.albedo.vllmpt.module.approval.core.service.ApprovalNotifyService;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyChannel;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyMessage;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalNotifyRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalNotifyServiceTest {

    @Test
    void sendsInboxMessageForCc() {
        ApprovalNotifyRegistry registry = mock(ApprovalNotifyRegistry.class);
        ApprovalNotifyChannel channel = mock(ApprovalNotifyChannel.class);
        when(registry.find("INBOX")).thenReturn(channel);
        ApprovalNotifyService service = new ApprovalNotifyService(registry);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(1L);
        instance.setTitle("请假申请");
        instance.setInstanceNo("AP1");
        ApprovalTemplateNode node = new ApprovalTemplateNode();
        node.setNodeKey("cc_hr");

        service.sendCc(instance, node, 4L);

        ArgumentCaptor<ApprovalNotifyMessage> captor = ArgumentCaptor.forClass(ApprovalNotifyMessage.class);
        verify(channel).send(captor.capture());
        assertEquals("CC:1:cc_hr:4", captor.getValue().notifyKey());
        assertEquals("CC", captor.getValue().type());
        assertEquals(4L, captor.getValue().receiverId());
    }

    @Test
    void inboxChannelWritesOutbox() {
        ApprovalNotificationMapper mapper = mock(ApprovalNotificationMapper.class);
        when(mapper.insertIgnore(anyString(), any(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        InboxNotifyChannel channel = new InboxNotifyChannel(mapper);

        channel.send(new ApprovalNotifyMessage("CC:1:cc_hr:4", 1L, null, 4L,
                "CC", "INBOX", "抄送提醒", "内容"));

        verify(mapper).insertIgnore(anyString(), any(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
