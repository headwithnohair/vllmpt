package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批抄送 approval_cc。
 * <p>
 * 抄送只做消息触达，不生成待办、不阻塞流程。
 */
@Data
@TableName("approval_cc")
public class ApprovalCc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String nodeKey;

    private Long receiverId;

    /** 发送渠道，多个用逗号分隔，如 INBOX,DINGTALK */
    private String channel;

    /** 0=未读 1=已读 */
    private Integer readStatus;

    private LocalDateTime createdAt;
}
