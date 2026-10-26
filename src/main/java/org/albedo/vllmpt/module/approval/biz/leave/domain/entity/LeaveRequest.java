package org.albedo.vllmpt.module.approval.biz.leave.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假业务表 leave_request。
 * <p>
 * 只存业务字段，审批状态由 approval_instance 管理；{@code approvalStatus} 为冗余字段，
 * 便于业务列表查询，由 {@code LeaveBizHandler.applyResult} 同步。
 */
@Data
@TableName("leave_request")
public class LeaveRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long approvalInstanceId;

    private Long applicantId;

    /** ANNUAL / SICK / PERSONAL / COMP_TIME */
    private String leaveType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 服务端计算，支持 0.5 天 */
    private BigDecimal leaveDays;

    private String reason;

    /** 附件 URL 列表 JSON */
    private String attachmentUrls;

    /** 冗余的审批状态：RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED */
    private String approvalStatus;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
