package org.albedo.vllmpt.module.chat.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.config.TokenQuotaProperties;
import org.albedo.vllmpt.module.chat.model.vo.QuotaLimit;
import org.albedo.vllmpt.module.quota.mapper.AiUserTokenQuotaMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserTokenQuota;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
public class QuotaLimitResolver {


    private final  AiUserTokenQuotaMapper aiUserTokenQuotaMapper;
    private final  RedissonClient redissonClient;
    private final  TokenQuotaProperties tokenQuotaProperties;

    // 不限制配额的统一返回值（enabled=false 时 dailyLimit 不会被使用，0L 只是占位）
    private static final QuotaLimit UNLIMITED = new QuotaLimit(0L, false);

    // 不限制配额在缓存里的编码值
    private static final String CACHE_VALUE_UNLIMITED = "UNLIMITED";

    public QuotaLimitResolver(AiUserTokenQuotaMapper aiUserTokenQuotaMapper,
                             RedissonClient redissonClient,
                             TokenQuotaProperties tokenQuotaProperties) {
        this.aiUserTokenQuotaMapper = aiUserTokenQuotaMapper;
        this.redissonClient = redissonClient;
        this.tokenQuotaProperties = tokenQuotaProperties;
    }

    public QuotaLimit resolve(String userId) {

        RBucket<String> bucket = redissonClient.getBucket(
                RedisKey.quotaLimit(userId), StringCodec.INSTANCE);
        String cached = bucket.get();
        if (cached != null) {
            // "UNLIMITED" → UNLIMITED；数字串 → new QuotaLimit(n, true)
            return parse(cached);
        }
        AiUserTokenQuota row = aiUserTokenQuotaMapper.selectOne(
                new LambdaQueryWrapper<AiUserTokenQuota>().eq(AiUserTokenQuota::getUserId, userId), false);


        QuotaLimit result;
        long ttlOverride = -1;

        if (row  ==null)
        {
            // 没有配额记录 → 用配置里的默认日额度，而不是硬编码
            result = new QuotaLimit(tokenQuotaProperties.getDefaultDailyLimit(), true);

        }else if (row .getQuotaEnabled() != 1) {
            // 显式关闭配额 → 不限制
            result = UNLIMITED;

        }else {
            LocalDateTime now  = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            LocalDateTime from = row .getEffectiveFrom();
            LocalDateTime to   = row .getEffectiveTo();

            boolean notYet  = from != null && from.isAfter(now);
            boolean expired = to   != null && to.isBefore(now);

            if (notYet  || expired ){

                if (notYet) {
                    ttlOverride = Duration.between(now, from).getSeconds();
                }
                result =  UNLIMITED;
            }else {
                result = new QuotaLimit(row.getDailyLimit(), true);
            }
        }

        long ttl = ttlOverride > 0 ? ttlOverride : tokenQuotaProperties.getCacheTtlSeconds();
        bucket.set(encode(result), Duration.ofSeconds(ttl));
        return result;
    }

    // ==================== 缓存值编解码 ====================
    // 这两个方法设计成一对：encode 负责写缓存，parse 负责读缓存，改动时一定要一起改。

    /**
     * QuotaLimit → 缓存值
     * <p>
     * 规则很简单：
     * <ul>
     *   <li>enabled = true  → 额度数字的字符串，例如 "100000"</li>
     *   <li>enabled = false → "UNLIMITED"</li>
     * </ul>
     * 好处是缓存里就是一个人类可读的短字符串，redis-cli 里 GET 出来能直接看懂，
     * 也不用担心 JSON 反序列化的开销和兼容问题。
     */
    private String encode(QuotaLimit limit) {
        return limit.enabled() ? String.valueOf(limit.dailyLimit()) : CACHE_VALUE_UNLIMITED;
    }

    /**
     * 缓存值 → QuotaLimit（encode 的逆向）
     * <p>
     * "UNLIMITED" → UNLIMITED；数字串 → new QuotaLimit(n, true)
     * <p>
     * 解析失败（脏数据 / 被人手动改坏了）时按"不限制"处理：
     * 宁可少限制一点，也不要因为缓存问题把用户误伤成"额度 0"而被拒绝。
     */
    private QuotaLimit parse(String cached) {
        if (CACHE_VALUE_UNLIMITED.equalsIgnoreCase(cached)) {
            return UNLIMITED;
        }
        try {
            return new QuotaLimit(Long.parseLong(cached), true);
        } catch (NumberFormatException e) {
            log.warn("配额缓存值无法解析，按不限制处理: value={}", cached);
            return UNLIMITED;
        }
    }





}
