package org.albedo.vllmpt.module.chat.service;


import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.config.ConcurrentLimitProperties;
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
    public TokenQuotaService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;

    }

    private static final String Check_User_Token_ADD = """
    local key   = KEYS[1]
    local estimateTokens = tonumber(ARGV[1])
    local dailyLimit   = tonumber(ARGV[2])
    local ttlSeconds = tonumber(ARGV[3])
    
    local used  =tonumber(redis.call("GET",key ) or '0')
    if (used + estimateTokens > dailyLimit) then
       return -1
    end
    
    local after = redis.call('INCRBY', key, estimateTokens)
    
    if redis.call('TTL', key) < 0 then
        redis.call('EXPIRE', key, ttlSeconds)
    end

    return after;
    """;

    public Long preDeduct(String userId,Long estimate){
        String quota = RedisKey.quota(userId, DateFormatUtils.format(new Date(), "yyyyMMdd"));
        Long pp = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                Check_User_Token_ADD,
                RScript.ReturnType.LONG,
                List.of(quota),
                estimate,
                1000 * 10000L,
                TimeUnit.DAYS.toSeconds(1)
        );
        return pp ;

    }


    public  void settle(QuotaLimitResolver reservation, long actualTokens, String modelName){
        // 没告诉我要实现什么
    }
}
