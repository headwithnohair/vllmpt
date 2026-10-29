package org.albedo.vllmpt.module.approval.biz.meeting.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交会议室预约。
 */
@Data
public class MeetingSubmitRequest {

    @NotNull(message = "会议室不能为空")
    private Long roomId;

    /** yyyy-MM-dd HH:mm */
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /** yyyy-MM-dd HH:mm */
    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    @NotNull(message = "参会人数不能为空")
    @Min(value = 1, message = "参会人数必须大于 0")
    private Integer attendees;

    @NotBlank(message = "会议用途不能为空")
    @Size(max = 500, message = "会议用途不能超过 500 字")
    private String purpose;

    private List<String> equipment;

    private Boolean hasExternalGuest;

    private String title;

    private String idempotencyKey;
}
