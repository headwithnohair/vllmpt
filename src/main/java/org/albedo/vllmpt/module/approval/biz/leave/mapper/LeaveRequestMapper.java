package org.albedo.vllmpt.module.approval.biz.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
}
