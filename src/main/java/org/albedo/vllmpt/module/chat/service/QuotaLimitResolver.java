package org.albedo.vllmpt.module.chat.service;


import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.model.vo.QuotaLimit;
import org.albedo.vllmpt.module.quota.mapper.AiUserTokenQuotaMapper;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class QuotaLimitResolver {


    private final  AiUserTokenQuotaMapper aiUserTokenQuotaMapper;
    private final  RedissonClient redissonClient;

    public QuotaLimitResolver(AiUserTokenQuotaMapper aiUserTokenQuotaMapper, RedissonClient redissonClient) {
        this.aiUserTokenQuotaMapper = aiUserTokenQuotaMapper;
        this.redissonClient = redissonClient;
    }


    public QuotaLimit resolve(String userId) {

        return  null;
    }





}
