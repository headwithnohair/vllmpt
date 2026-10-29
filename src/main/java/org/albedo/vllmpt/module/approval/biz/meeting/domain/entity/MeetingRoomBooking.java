package org.albedo.vllmpt.module.approval.biz.meeting.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室预约业务表 meeting_room_booking。
 */
@Data
@TableName("meeting_room_booking")
public class MeetingRoomBooking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long approvalInstanceId;

    private Long applicantId;

    private Long roomId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer attendees;

    private String purpose;

    /** 设备需求 JSON */
    private String equipment;

    private Integer hasExternalGuest;

    /** 冗余审批状态：RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED */
    private String approvalStatus;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
