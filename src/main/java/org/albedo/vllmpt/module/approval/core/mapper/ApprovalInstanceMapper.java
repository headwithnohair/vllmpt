package org.albedo.vllmpt.module.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstance> {

    /**
     * 加行锁读取实例。
     * <p>
     * 同一实例的审批动作串行执行，避免会签/或签并发时都认为还有待办而卡住流程。
     */
    @Select("SELECT * FROM approval_instance WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ApprovalInstance selectByIdForUpdate(@Param("id") Long id);
}
