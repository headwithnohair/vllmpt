package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批通知发件箱 approval_notification。
 * <p>
 * INBOX 渠道即时写 SENT；钉钉/飞书/邮件等外部渠道可先写 PENDING 再由重试任务发送。
 */
@Data
@TableName("approval_notification")
public class ApprovalNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notifyKey;

    private Long instanceId;

    private Long taskId;

    private Long receiverId;

    /** TODO / CC / RESULT / REMIND */
    @TableField("notify_type")
    private String type;

    /** INBOX / DINGTALK / FEISHU / EMAIL */
    private String channel;

    private String title;

    private String content;

    /** PENDING / SENT / FAILED */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
}
