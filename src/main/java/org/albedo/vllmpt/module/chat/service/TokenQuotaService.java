package org.albedo.vllmpt.module.chat.service;


import org.albedo.vllmpt.common.redis.RedisKey;

import org.albedo.vllmpt.module.chat.model.vo.QuotaReservation;
// ⚠️ 建议换掉：DateFormatUtils 来自 langchain4j / ES client 的"传递依赖"，能编译但不可靠；
//    而且它用 JVM 默认时区，换成 java.time + 显式 ZoneId.of("Asia/Shanghai") 更稳妥
import org.apache.commons.lang3.time.DateFormatUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TokenQuotaService {

    private  final RedissonClient redissonClient;
    private  final QuotaLimitResolver quotaLimitResolver;

    public TokenQuotaService(RedissonClient redissonClient, QuotaLimitResolver quotaLimitResolver) {
        this.redissonClient = redissonClient;

        this.quotaLimitResolver = quotaLimitResolver;
    }

    private static final String Check_User_Token_ADD = """
    local key   = KEYS[1]
    local estimateTokens = tonumber(ARGV[1])
    local dailyLimit   = tonumber(ARGV[2])
    local ttlSeconds = tonumber(ARGV[3])
    
    local used  =tonumber(redis.call("GET",key ) or '0')
    if dailyLimit > 0 and used + estimateTokens > dailyLimit then
       return -1
    end
    
    local after = redis.call('INCRBY', key, estimateTokens)
    
    if redis.call('TTL', key) < 0 then
        redis.call('EXPIRE', key, ttlSeconds)
    end

    return after;
    """;

    /**
     * ❌ 这个方法还没接线，有 4 个问题：
     *   1) 日额度硬编码 1000*10000L —— 应该来自 quotaLimitResolver.resolve(userId).dailyLimit()
     *   2) 构造函数里没有注入 QuotaLimitResolver，所以现在拿不到真实额度
     *   3) 返回值应该是 QuotaReservation（而不是 Long）——
     *      否则 settle 拿不到 quotaKey，跨零点时预扣与结算会落到两个不同的 key 上
     *   4) estimate 是 Long 直接传，而其他地方都是 String.valueOf(...)，风格统一一下
     */
    public Long preDeduct(String userId,Long estimate){
        // ⚠️ 每次都重新取"当天日期"。跨零点场景（23:59:59 预扣、00:00:01 结算）会写到两个 key 上，
        //    建议只在这里取一次，然后把 quotaKey + statDate 一起装进 QuotaReservation 带出去，结算时复用
        String quota = RedisKey.quota(userId, DateFormatUtils.format(new Date(), "yyyyMMdd"));
        Long pp = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                Check_User_Token_ADD,
                RScript.ReturnType.LONG,
                List.of(quota),
                estimate,
                quotaLimitResolver.resolve(userId).dailyLimit(),
                // ⚠️ 语义是"从此刻起 24 小时"，不是"到今天结束"。无害，但要知道区别
                TimeUnit.DAYS.toSeconds(1)
        );

        // ⚠️ 返回 -1 表示超额，调用方必须同时判 null 和 < 0
        return pp ;

    }


    /**
     * ❌ 方法体还是空的，需要实现 4 步（2/3/4 必须一次原子完成，否则会出现"额度退了但用量没记"）：
     *   1) diff = actualTokens - reservation.estimateTokens()   // 可能是负数，负数就是"退"
     *   2) diff != 0 时 → INCRBY reservation.quotaKey() diff
     *      ⚠️ 必须用 reservation 里存的 quotaKey，不要在这里重新算日期（跨零点会写歪）
     *   3) → HINCRBY RedisKey.Usage(reservation.userId(), reservation.statDate()) modelName actualTokens
     *      ❌ 但 QuotaReservation 现在没有 userId 字段，这一步拼不出 key，需要先给它补上
     *   4) → if TTL usageKey < 0 then EXPIRE usageKey ttlSeconds
     * <p>
     * ⚠️ 重要：差额回补与 enabled 无关。
     *    不管限制不限制，Lua 里都执行了 INCRBY estimate，所以两种情况都必须回补，
     *    否则那个计数器会越来越虚高。enabled 只影响 Lua 里的判限，不影响结算。
     */
    public void settle(QuotaReservation reservation, long actualTokens, String modelName){
  // 没告诉我要实现什么
    }
}
