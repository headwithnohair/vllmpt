package org.albedo.vllmpt.module.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserDailyTokenUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户每日 Token 用量表数据访问接口。
 * <p>
 * 本表的唯一键是 {@code uk_user_date_model (user_id, stat_date, model_name)}，
 * 对账落库必须走「累加式 upsert」，否则同一用户同一天同一模型的多次请求会互相覆盖。
 * <p>
 * 当日合计不要单独存一行，用 {@code SUM(total_tokens)} 聚合。
 */
@Mapper
public interface AiUserDailyTokenUsageMapper extends BaseMapper<AiUserDailyTokenUsage> {

    /**
     * 累加式写入一条用量记录（不存在则插入，存在则把各计数字段累加上去）。
     *
     * <p>调用方在 {@code TokenQuotaService.settle(...)} 里，
     * 每次请求结算后调用一次。</p>
     *
     * <p>⚠️ 实现要点（对应练习指南「步骤五」）：</p>
     * <ol>
     *   <li>用 {@code @Insert} 注解写 SQL，列至少包含：
     *       {@code user_id, stat_date, model_name, prompt_tokens, completion_tokens,
     *       total_tokens, request_count}；</li>
     *   <li>{@code request_count} 的插入值固定写 {@code 1}；</li>
     *   <li>{@code ON DUPLICATE KEY UPDATE} 里每一项都必须写成
     *       {@code 列名 = 列名 + 本次增量}，<b>不能</b>写成 {@code 列名 = 本次增量} ——
     *       后者会把历史累计值直接覆盖掉，是这张表最容易犯的错；</li>
     *   <li>{@code request_count} 每次固定 {@code + 1}；</li>
     *   <li>不要带上 {@code estimated_cost} —— 目前没有模型单价，传 {@code null} 会让
     *       {@code estimated_cost + NULL = NULL} 把累计值抹成空。
     *       以后接入计价时再加，且要传 {@code BigDecimal.ZERO} 而不是 {@code null}。</li>
     * </ol>
     *
     * <p>在实际实现之前，调用这个方法会抛
     * {@code BindingException: Invalid bound statement (not found)} ——
     * 没有任何 SQL 注解、也没有对应 XML 的接口方法，MyBatis 不会为它注册语句，
     * 这正是「还没写就调用不了」的表现。</p>
     *
     * @param usage 本次请求的用量，{@code statDate} 为当天、计数字段为本次增量
     * @return 受影响行数
     */
    int upsertDailyUsage(AiUserDailyTokenUsage usage);
    // TODO [步骤5-1] 给上面这个方法补上 @Insert 注解与 SQL（注意必须写成累加，见上方说明）
}
