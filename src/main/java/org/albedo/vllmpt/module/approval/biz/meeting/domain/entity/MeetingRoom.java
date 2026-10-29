package org.albedo.vllmpt.module.approval.biz.meeting.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室主数据 meeting_room。
 */
@Data
@TableName("meeting_room")
public class MeetingRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomCode;

    private String name;

    private Integer capacity;

    private String location;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
