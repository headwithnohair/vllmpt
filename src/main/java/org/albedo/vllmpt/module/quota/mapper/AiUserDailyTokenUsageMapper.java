package org.albedo.vllmpt.module.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserDailyTokenUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户每日 Token 用量表数据访问接口。
 * <p>
 * 本表只继承 BaseMapper，没有提供「累加式 upsert」方法 —— 那是后续开发的内容。
 * 需要用累加式写入时，建议在这里加一个自定义方法，SQL 形如：
 * <pre>
 * INSERT INTO ai_user_daily_token_usage (...) VALUES (...)
 * ON DUPLICATE KEY UPDATE total_tokens = total_tokens + VALUES(total_tokens), ...
 * </pre>
 */
@Mapper
public interface AiUserDailyTokenUsageMapper extends BaseMapper<AiUserDailyTokenUsage> {
}
