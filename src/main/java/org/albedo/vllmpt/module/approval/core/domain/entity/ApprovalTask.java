package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务 approval_task。
 */
@Data
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：instanceId:nodeKey:assigneeId:sequenceNo */
    private String taskKey;

    private Long instanceId;

    private String nodeKey;

    private String nodeName;

    private Long assigneeId;

    private String assigneeType;

    /** APPROVAL / CC / TRANSFER / ADD_SIGN */
    private String taskType;

    /** WAITING / PENDING / APPROVED / REJECTED / SKIPPED / TRANSFERRED / CANCELED */
    private String status;

    /** ANY / ALL / SEQUENTIAL，创建任务时从节点快照下来 */
    private String approvalMode;

    private String action;

    private String comment;

    private LocalDateTime dueAt;

    /** 顺序审批序号，从 1 开始 */
    private Integer sequenceNo;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
