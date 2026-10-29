package org.albedo.vllmpt.module.approval.biz.travel.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.albedo.vllmpt.module.approval.biz.travel.domain.entity.TravelExpense;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;

/**
 * 差旅费详情：审批实例 + 业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelDetailVO {

    private InstanceDetailVO approval;

    private TravelExpense travel;
}
