package org.albedo.vllmpt.module.approval.biz.leave.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;

/**
 * 请假详情：审批实例 + 请假业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDetailVO {

    private InstanceDetailVO approval;

    private LeaveRequest leave;
}
