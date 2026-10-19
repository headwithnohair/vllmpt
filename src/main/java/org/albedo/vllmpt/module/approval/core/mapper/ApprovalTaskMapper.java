package org.albedo.vllmpt.module.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /**
     * 加行锁读取任务，拿到实例锁后用它复核任务是否仍可处理。
     */
    @Select("SELECT * FROM approval_task WHERE id = #{id} FOR UPDATE")
    ApprovalTask selectByIdForUpdate(@Param("id") Long id);
}
