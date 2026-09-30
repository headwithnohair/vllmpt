package org.albedo.vllmpt.module.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserTokenQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Token 配额表数据访问接口。
 */
@Mapper
public interface AiUserTokenQuotaMapper extends BaseMapper<AiUserTokenQuota> {
}
