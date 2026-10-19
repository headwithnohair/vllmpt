package org.albedo.vllmpt.module.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalTemplateMapper extends BaseMapper<ApprovalTemplate> {
}
