package org.albedo.vllmpt.module.approval.biz.meeting.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoomBooking;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;

/**
 * 会议室预约详情：审批实例 + 业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDetailVO {

    private InstanceDetailVO approval;

    private MeetingRoomBooking booking;
}
