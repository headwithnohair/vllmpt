package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流水 approval_record。
 */
@Data
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String nodeKey;

    private Long taskId;

    private Long operatorId;

    /** USER / AI / SYSTEM */
    private String operatorType;

    /** SUBMIT / APPROVE / REJECT / WITHDRAW / ... */
    private String action;

    private String comment;

    private LocalDateTime createdAt;
}
