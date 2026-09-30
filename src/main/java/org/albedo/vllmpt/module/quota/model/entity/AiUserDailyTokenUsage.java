package org.albedo.vllmpt.module.quota.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日 Token 用量表 ai_user_daily_token_usage（按「用户 + 日期 + 模型」一行）
 * <p>
 * 建表脚本：doc/sql/init_user_quota.sql
 * <p>
 * 唯一键 (user_id, stat_date, model_name) 是给「对账落库」准备的，对账 SQL 必须写成累加：
 * <pre>
 * INSERT INTO ai_user_daily_token_usage
 *     (user_id, stat_date, model_name, prompt_tokens, completion_tokens, total_tokens, request_count)
 * VALUES (?, ?, ?, ?, ?, ?, ?)
 * ON DUPLICATE KEY UPDATE
 *     prompt_tokens     = prompt_tokens     + VALUES(prompt_tokens),
 *     completion_tokens = completion_tokens + VALUES(completion_tokens),
 *     total_tokens      = total_tokens      + VALUES(total_tokens),
 *     request_count     = request_count     + VALUES(request_count)
 * </pre>
 * 写成 {@code total_tokens = VALUES(total_tokens)} 会把累计值直接覆盖掉。
 * <p>
 * 当日合计不要单独存行，用 {@code SUM(total_tokens)} 聚合。
 */
@Data
@TableName("ai_user_daily_token_usage")
public class AiUserDailyTokenUsage {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 统计日期（Asia/Shanghai） */
    private LocalDate statDate;

    /** 模型名（对应 ai.models[*].model-id） */
    private String modelName;

    /** 输入 token 累计 */
    private Long promptTokens;

    /** 输出 token 累计 */
    private Long completionTokens;

    /** 总 token 累计 */
    private Long totalTokens;

    /** 请求次数 */
    private Integer requestCount;

    /** 预估费用（模型单价 × total_tokens） */
    private BigDecimal estimatedCost;

    /** 逻辑删除：0=正常 1=已删除（本表不建议逻辑删除，见建表脚本注释） */
    @TableLogic
    private Integer deleted;

    /** 创建时间（数据库默认值填充） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库默认值填充） */
    private LocalDateTime updatedAt;
}
