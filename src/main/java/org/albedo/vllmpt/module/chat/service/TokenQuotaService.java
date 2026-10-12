package org.albedo.vllmpt.module.chat.service;

import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.config.TokenQuotaProperties;
import org.albedo.vllmpt.module.chat.model.vo.QuotaLimit;
import org.albedo.vllmpt.module.chat.model.vo.QuotaReservation;
import org.albedo.vllmpt.module.quota.mapper.AiUserDailyTokenUsageMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserDailyTokenUsage;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <h3>每日 Token 配额服务（Redis 预扣 + 结算）</h3>
 *
 * <p>三阶段闭环：</p>
 * <ol>
 *   <li><b>预扣</b>（{@link #preDeduct}）：请求进入时按固定估算值原子占用额度，
 *       超额直接拒绝（业务码 402），不消耗算力；</li>
 *   <li><b>结算</b>（{@link #settle}）：模型返回后按真实用量「多退少补」，
 *       同时按模型累计用量，并把结果落库；</li>
 *   <li><b>兜底</b>：额度计数器与用量 Hash 都带 TTL，避免键无限堆积。</li>
 * </ol>
 *
 * <p>Redis 键：</p>
 * <ul>
 *   <li>{@code ai:quota:{userId}:{yyyyMMdd}} —— String，当日已用（含预扣）token</li>
 *   <li>{@code ai:usage:{userId}:{yyyyMMdd}} —— Hash，field = 模型名，value = 该模型当日累计 token</li>
 *   <li>{@code ai:quota:limit:{userId}} —— String，日额度缓存，由 {@link QuotaLimitResolver} 维护</li>
 * </ul>
 *
 * <p>⚠️ 作业说明：{@link #preDeduct}、{@link #settle} 与 {@link #SETTLE_LUA} 需要你自己补全，
 * 对应练习指南的「步骤三 / 步骤四」。</p>
 */
@Slf4j
@Service
public class TokenQuotaService {

    /** 额度不足的业务码：402 Payment Required，与「并发过多」的 429 区分开 */
    public static final int CODE_TOKEN_QUOTA_EXCEEDED = 402;

    /** 统计日期与 Redis 键统一使用东八区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter STAT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonClient redissonClient;
    private final QuotaLimitResolver quotaLimitResolver;
    private final TokenQuotaProperties tokenQuotaProperties;
    private final AiUserDailyTokenUsageMapper aiUserDailyTokenUsageMapper;

    public TokenQuotaService(RedissonClient redissonClient,
                             QuotaLimitResolver quotaLimitResolver,
                             TokenQuotaProperties tokenQuotaProperties,
                             AiUserDailyTokenUsageMapper aiUserDailyTokenUsageMapper) {
        this.redissonClient = redissonClient;
        this.quotaLimitResolver = quotaLimitResolver;
        this.tokenQuotaProperties = tokenQuotaProperties;
        this.aiUserDailyTokenUsageMapper = aiUserDailyTokenUsageMapper;
    }

    /**
     * 预扣脚本：一次 RTT 内完成「取当前值 → 判限 → 累加 → 设置兜底过期」。
     * <p>
     * KEYS[1] = {@code ai:quota:{userId}:{yyyyMMdd}}<br>
     * ARGV[1] = 本次预扣的估算值<br>
     * ARGV[2] = 日额度；&lt;= 0 表示不限制<br>
     * ARGV[3] = 兜底过期秒数<br>
     * 返回：累加后的已用值；返回 {@code -1} 表示超额
     */
    private static final String PRE_DEDUCT_LUA = """
            local key = KEYS[1]
            local estimateTokens = tonumber(ARGV[1])
            local dailyLimit = tonumber(ARGV[2])
            local ttlSeconds = tonumber(ARGV[3])

            local used = tonumber(redis.call('GET', key) or '0')
            if dailyLimit > 0 and used + estimateTokens > dailyLimit then
                return -1
            end

            local after = redis.call('INCRBY', key, estimateTokens)

            if redis.call('TTL', key) < 0 then
                redis.call('EXPIRE', key, ttlSeconds)
            end

            return after
            """;

    /**
     * 结算脚本：一次原子完成「差额回补 + 分模型累计 + 兜底过期」。
     * <p>
     * 三件事写在一个脚本里，是为了避免出现「额度退了但用量没记」的中间态。
     *
     * <pre>
     * KEYS[1] = ai:quota:{userId}:{yyyyMMdd}   当日额度计数器
     * KEYS[2] = ai:usage:{userId}:{yyyyMMdd}   分模型用量 Hash
     * ARGV[1] = diff = actual - estimate       （可为负，负数即「退」）
     * ARGV[2] = modelName
     * ARGV[3] = actualTokens
     * ARGV[4] = ttlSeconds
     * 返回：固定 1
     * </pre>
     */
    private static final String SETTLE_LUA = """
            local quotaKey= KEYS[1]
            local usageKey= KEYS[2]
            local diff = tonumber(ARGV[1])
            local modelName =  ARGV[2]
            local actualTokens =  ARGV[3]
            local ttlSeconds =  ARGV[4]
           
           
            if diff ~= 0 then
                redis.call("INCRBY",quotaKey,diff)
                local after = tonumber(redis.call("GET", quotaKey))
                if after and after < 0 then
                    redis.call("SET", quotaKey, 0)
                    redis.call("EXPIRE", quotaKey, ttlSeconds)
                end
            end
            redis.call("HINCRBY",usageKey,modelName,actualTokens)
            
            if redis.call("TTL", usageKey) < 0 then
                        redis.call("EXPIRE", usageKey, ttlSeconds)
            end
            return 1
            
            """;

    /**
     * 预扣当日额度。
     *
     * <p>成功返回一份「凭证」，结算时必须用凭证里的键，不能重新计算日期 ——
     * 否则 23:59:59 预扣、00:00:01 结算的请求会把差额退到第二天的计数器上。</p>
     *
     * @param userId   Redis 键里的用户标识（与并发限制共用同一套标识）
     * @param estimate 本次预扣的估算值
     * @return 预扣凭证
     * @throws BusinessException 额度不足时抛出，业务码 {@link #CODE_TOKEN_QUOTA_EXCEEDED}
     */
    public QuotaReservation preDeduct(String userId, long estimate) {
               String statDate = LocalDate.now(ZONE).format(STAT_DATE_FORMAT);
               String quotaKey = RedisKey.quota(userId, statDate);
               QuotaLimit limit = quotaLimitResolver.resolve(userId);
               long dailyLimitArg = limit.enabled() ? limit.dailyLimit() : -1L;
               Long after = redissonClient.getScript(StringCodec.INSTANCE).eval(
                   RScript.Mode.READ_WRITE,
                   PRE_DEDUCT_LUA,
                   RScript.ReturnType.LONG,
                   List.of(quotaKey),
                   String.valueOf(estimate),
                   String.valueOf(dailyLimitArg),
                   String.valueOf(tokenQuotaProperties.getCounterTtlSeconds()));

                if(after ==null || after <0){
                    log.warn("CODE_TOKEN_QUOTA_EXCEEDED,userId:{},estimate:{},dailyLimitArg:{}",userId,estimate,dailyLimitArg);
                    throw new BusinessException(CODE_TOKEN_QUOTA_EXCEEDED, "今日 Token 额度已用完，请明天再试");
                }
               return new QuotaReservation(userId, quotaKey, statDate, estimate, limit.enabled());
    }

    /**
     * 结算：按真实用量多退少补，按模型累计用量，并把结果    落库。
     *
     * <p><b>整个方法体必须包在 try/catch 里，catch 只打日志、绝不向外抛。</b>
     * 它运行在接入层的 {@code finally} 中，一旦抛出，后面的「释放并发额度」就不会执行。</p>
     *
     * @param reservation 预扣凭证；为 {@code null} 表示预扣没成功，直接返回
     * @param usage       模型返回的用量；可能为 {@code null}（流式响应通常不带用量）
     * @param modelName   模型名，用于分模型累计与落库（表里 {@code model_name} 是唯一键的一部分，不能为 null）
     */
    public void settle(QuotaReservation reservation, TokenUsage usage, String modelName) {
           if (reservation == null) { return; }
           try {

           long actual;
           if (usage != null && usage.totalTokenCount() != null) {
               actual = usage.totalTokenCount();
           } else {
               actual = reservation.estimateTokens();
               log.warn("结算时取不到真实用量，按预扣估算值计费: userId={}, statDate={}, estimate={}, usageNull={}",
                       reservation.userId(), reservation.statDate(), reservation.estimateTokens(), usage == null);
           }
           long diff = actual - reservation.estimateTokens();
           redissonClient.getScript(StringCodec.INSTANCE).eval(
               RScript.Mode.READ_WRITE,
               SETTLE_LUA,
               RScript.ReturnType.LONG,
               List.of(reservation.quotaKey(),
                       RedisKey.usage(reservation.userId(), reservation.statDate())),
               String.valueOf(diff),
               modelName,
               String.valueOf(actual),
               String.valueOf(tokenQuotaProperties.getCounterTtlSeconds()));
           persistDailyUsage(reservation, usage, modelName, actual);


           } catch (Exception e) {
               log.error("结算时出错，{}", e.getMessage(),e);            // 只打日志，不要 rethrow
           }

    }

    /**
     * 把本次用量累加落库到 {@code ai_user_daily_token_usage}。
     *
     * <p>由 {@link #settle} 调用，异常由 settle 统一捕获，这里不用再包 try/catch。</p>
     *
     * @param reservation 预扣凭证，提供 userId 与 statDate
     * @param usage       模型用量；为 {@code null} 时直接跳过落库（没拿到用量就不要写不准的行）
     * @param modelName   模型名
     * @param actual      本次真实消耗（已由 settle 兜底算好）
     */
    private void persistDailyUsage(QuotaReservation reservation, TokenUsage usage,
                                   String modelName, long actual) {
        if (usage ==null) {return;}
        try{
            Long dbUserId = Long.parseLong(reservation.userId());
            LocalDate statDate=LocalDate.parse(reservation.statDate(), STAT_DATE_FORMAT);
            AiUserDailyTokenUsage row =new AiUserDailyTokenUsage();
            row.setUserId(dbUserId);
            row.setStatDate(statDate);
            row.setModelName(modelName);
            row.setPromptTokens(toLong(usage.inputTokenCount()));
            row.setCompletionTokens(toLong(usage.outputTokenCount()));
            row.setTotalTokens(actual);
            row.setRequestCount(1);
            aiUserDailyTokenUsageMapper.upsertDailyUsage(row);
            log.debug("upsertDailyUsage,rowId:{},userID:{}," +
                            "inputTokenCount:{},outputTokenCount:{}",
                    row.getId(),dbUserId,usage.inputTokenCount(),usage.outputTokenCount());
        }catch (Exception e){
            log.debug("转换错误，{}", e.getMessage(),e);            // 只打日志，不要 rethrow

            return;
        }
    }
    private static long toLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }
    /**
     * 全额退还预扣额度 —— 用于「请求被拒绝、流水线根本没跑起来」的路径，
     * 典型场景是会话互斥锁没抢到（模型一次都没调用，当然不该扣费）。
     *
     * <p>调用时机：接入层 {@code finally} 里，判定「会话锁没拿到」时调它而不是
     * {@link #settle}。若用 settle，usage 为 null 会被当成「拿不到用量」按估算值计费，
     * 于是被拒绝的请求也扣了 2000 —— 这是很容易忽略的一处错误计费。</p>
     *
     * <p>与 {@link #settle} 一样，内部吞掉全部异常（它同样运行在 {@code finally} 中）。</p>
     *
     * <p>这里故意不走 Lua：退还只改一个键，「读旧值再写回」之间的竞态最多让计数器
     * 短暂偏小（后续请求更容易通过），不会造成超额，属于可接受的取舍。
     * 真正的结算路径（{@link #settle}）必须原子，两者要求不同。</p>
     */
    public void refund(QuotaReservation reservation) {
        if (reservation == null) {
            return;
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(reservation.quotaKey(), StringCodec.INSTANCE);
            String current = bucket.get();
            if (current == null) {
                // 键已经过期消失，说明预扣的占用已经自然消失，无需退还
                return;
            }
            long after = Math.max(0L, Long.parseLong(current) - reservation.estimateTokens());
            bucket.set(String.valueOf(after), Duration.ofSeconds(tokenQuotaProperties.getCounterTtlSeconds()));
            log.info("退还预扣额度: userId={}, statDate={}, 退还={}, 退还后={}",
                    reservation.userId(), reservation.statDate(), reservation.estimateTokens(), after);
        } catch (Exception e) {
            log.error("退还预扣额度失败: userId={}, statDate={}",
                    reservation.userId(), reservation.statDate(), e);
        }
    }
}
