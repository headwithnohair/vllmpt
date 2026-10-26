package org.albedo.vllmpt.module.approval.biz.leave.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交请假申请。
 */
@Data
public class LeaveSubmitRequest {

    /** ANNUAL / SICK / PERSONAL / COMP_TIME */
    @NotBlank(message = "请假类型不能为空")
    private String leaveType;

    /** yyyy-MM-dd HH:mm */
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /** yyyy-MM-dd HH:mm */
    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    @NotBlank(message = "请假事由不能为空")
    @Size(max = 500, message = "请假事由不能超过 500 字")
    private String reason;

    private List<String> attachmentUrls;

    private String title;

    private String idempotencyKey;
}
