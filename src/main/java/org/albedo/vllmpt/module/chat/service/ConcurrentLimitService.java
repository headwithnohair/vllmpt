package org.albedo.vllmpt.module.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.chat.config.ConcurrentLimitProperties;
import org.redisson.api.RScript;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis ZSet + Lua 的单用户并发推理任务数限制。
 * 数据结构：key = {@code ai:concurrent:{userId}}，member = requestId，score = 发起时间戳(ms)。
 * 窗口内的 member 数量即为当前活跃的推理任务数。
 */
@Slf4j
@Service
public class ConcurrentLimitService {

    /**
     * 获取并发额度的 Lua 脚本
     * 入参约定：
     * <pre>
     * KEYS[1] = ai:concurrent:{userId}
     * ARGV[1] = nowMs   ARGV[2] = windowMs   ARGV[3] = limit   ARGV[4] = requestId   ARGV[5] = ttlSec
     * </pre>
     * 需要按顺序完成 4 件事：
     * <ol>
     *   <li>ZREMRANGEBYSCORE：清掉 score &lt; now - window 的僵尸 member（防止 SSE 异常断连导致额度泄漏）</li>
     *   <li>ZCARD：统计当前窗口内的活跃请求数</li>
     *   <li>若 count &ge; limit，return 0（拒绝）</li>
     *   <li>否则 ZADD 入队（score=now，member=requestId）+ EXPIRE 设置兜底过期，return 1（放行）</li>
     * </ol>
     */
    private static final String LUA_ACQUIRE = """
            local key = KEYS[1]
            local  nowMs =tonumber(ARGV[1])
            local  windowMs =tonumber(ARGV[2])
            local  limit =tonumber(ARGV[3])
            local  requestId =ARGV[4]
            local  ttlSec =tonumber(ARGV[5])
            local  maxMs =nowMs -windowMs
   
   
            redis.call("ZREMRANGEBYSCORE", key, "-inf","(" .. maxMs)   --删除分数小于 maxMs的元素
            local allCount = redis.call('ZCARD', key)--统计当前窗口内的活跃请求数
            
            if  allCount>=limit  then
                --请求满了 拒绝
                return 0
            end
            
          
             redis.call("ZADD",key,nowMs,requestId) -- 添加
             redis.call('EXPIRE', key, ttlSec)  -- 为当前key设置 过期时间 --如果长时间没有该userid的请求,则将其从redis缓存删除
            return 1
            """;

    private final RedissonClient redissonClient;
    private final ConcurrentLimitProperties props;

    public ConcurrentLimitService(RedissonClient redissonClient, ConcurrentLimitProperties props) {
        this.redissonClient = redissonClient;
        this.props = props;
    }

    /**
     * 尝试获取一个并发额度。
     * @return true 放行；false 并发已满
     */
    public boolean tryAcquire(String userId, String requestId) {

        // 1. 准备 Key
        String key = RedisKey.concurrent(userId);

        // 2. 准备参数
        long nowMs = System.currentTimeMillis();
        long windowMs = props.getWindowSeconds() * 1000L; // 秒转毫秒
        int limit = props.getLimit();
        int ttlSec = props.getKeyTtlSeconds();
        // 3. 执行 Lua 脚本
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                LUA_ACQUIRE,
                RScript.ReturnType.LONG,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(limit),
                requestId,
                String.valueOf(ttlSec)
        );
        if (result == null || result != 1L) {
            log.warn("未获取并发额度,已达上限, userId: {}, requestId: {}", userId, requestId);
            return false;
        }
       return true;
    }

    /**
     * 释放并发额度。
     */
    public void release(String userId, String requestId) {

        try {
           boolean removed= redissonClient.getScoredSortedSet(RedisKey.concurrent(userId),StringCodec.INSTANCE).remove(requestId);
            if (!removed)
            {
                log.debug("释放额度时 member 不存在（可能已过期或重复释放）: userId={}, requestId={}", userId, requestId);
            }
        }catch (Exception e){
            log.error("移除额度失败,请检查错误,userId: {}, requestId: {} ", userId, requestId,e);
        }


    }

    /**
     * 仅统计窗口内的活跃任务数，用于排查问题（不参与限流判断）。
     */
    public int currentCount(String userId) {
        long minScore = System.currentTimeMillis() - props.getWindowSeconds() * 1000L;
        RScoredSortedSet<String> zset =
                redissonClient.getScoredSortedSet(RedisKey.concurrent(userId), StringCodec.INSTANCE);
        return zset.count(minScore, true, Double.POSITIVE_INFINITY, true);
    }
}
