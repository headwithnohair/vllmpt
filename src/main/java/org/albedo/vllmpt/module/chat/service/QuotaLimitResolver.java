package org.albedo.vllmpt.module.chat.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.model.vo.QuotaLimit;
import org.albedo.vllmpt.module.quota.mapper.AiUserTokenQuotaMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserTokenQuota;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;


@Service
public class QuotaLimitResolver {


    private final  AiUserTokenQuotaMapper aiUserTokenQuotaMapper;
    private final  RedissonClient redissonClient;

    public QuotaLimitResolver(AiUserTokenQuotaMapper aiUserTokenQuotaMapper, RedissonClient redissonClient) {
        this.aiUserTokenQuotaMapper = aiUserTokenQuotaMapper;
        this.redissonClient = redissonClient;
    }

    private final String GET_USER_QUOTA_LIMIT = """
            local  key = KEYS[1]
            
            local res = redis.call("GET",key)
            -- 这里如果查询为空是返回什么?是否需要额外防空处理?
            return res ;
            
            
            """;
    public QuotaLimit resolve(String userId) {

        String pp  = RedisKey.quotaLimit(userId);
        Long  rest = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_ONLY,
                GET_USER_QUOTA_LIMIT,
                RScript.ReturnType.LONG,
                List.of(pp)
        );
        if (rest<0) {
            LambdaQueryWrapper<AiUserTokenQuota> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(AiUserTokenQuota::getUserId,userId);
            // ✅ selectOne 补了 false，多条时不会再抛 TooManyResultsException
            AiUserTokenQuota aiUserTokenQuota= aiUserTokenQuotaMapper.selectOne(queryWrapper,false);
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

            boolean notYetEffective = aiUserTokenQuota.getEffectiveFrom() != null
                        && aiUserTokenQuota.getEffectiveFrom().isAfter(now);

            boolean expired = aiUserTokenQuota.getEffectiveTo() != null
                        && aiUserTokenQuota.getEffectiveTo().isBefore(now);

            // 这里的代码很别扭,怎么优化?我知道第四步执行不了
            if (notYetEffective ){

                return  new QuotaLimit(0L,false);
            } else if ( expired ) {

                return  new QuotaLimit( 0L,false);
            }else {
                return  new QuotaLimit(aiUserTokenQuota.getDailyLimit(),true);
            }
        }

        //    notYetEffective → enabled = false，并且缓存 TTL 要缩短到 effectiveFrom
//        redissonClient.getbu
        // ---------- 第四步：回填缓存 + 返回 ----------
        // ❌ 还没写：把结果写回 ai:quota:limit:{userId}（默认值也要写，防穿透），然后 return
        // ❌ 直接 return null 会让调用方 NPE

        return  null;
    }





}
