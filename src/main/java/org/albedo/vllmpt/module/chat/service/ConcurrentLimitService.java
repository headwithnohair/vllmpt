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
 * <p>
 * 数据结构：key = {@code ai:concurrent:{userId}}，member = requestId，score = 发起时间戳(ms)。
 * 窗口内的 member 数量即为当前活跃的推理任务数。
 *
 * <h3>============ 练习说明 ============</h3>
 * 本类中标了 TODO 的 3 个位置需要你自己实现（对应计划文档的「步骤 3」）。
 * 实现前先把下面 4 个坑想清楚：
 * <ol>
 *   <li>{@code redissonClient.getScript(...)} 必须显式传 {@code StringCodec.INSTANCE}，
 *       否则会用全局 codec（默认 Kryo5）把字符串写成二进制，导致 ZCARD 永远是 0，限制永不触发。</li>
 *   <li>脚本的 ARGV 统一用 {@code String.valueOf(...)} 传，避免 codec 与类型差异。</li>
 *   <li>返回类型用 {@code RScript.ReturnType.LONG} —— Redisson 4.4.0 里没有 INTEGER，已改名为 LONG。</li>
 *   <li>{@code release} 必须吞掉异常并只打日志，否则会掩盖真正的业务异常。</li>
 * </ol>
 */
@Slf4j
@Service
public class ConcurrentLimitService {

    /**
     * 获取并发额度的 Lua 脚本 —— TODO [步骤3-1] 自己实现。
     * <p>
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
     * 提示：Lua 里所有参数都是字符串，数字比较前记得 {@code tonumber(ARGV[x])}。
     */
    private static final String LUA_ACQUIRE = """
            local key = KEYS[1]
            local  nowMs =tonumber(ARGV[1])
            local  windowMs =tonumber(ARGV[2])
            local  limit =tonumber(ARGV[3])
            local  requestId =ARGV[4]
            local  ttlSec =tonumber(ARGV[5])
            local  maxMs =nowMs -windowMs
   
   
            redis.call("ZREMRANGEBYSCORE", key, "-inf","(" .. maxMs)
            local allCount = redis.call('ZCARD', key)
            
            if  allCount>=limit  then
                --请求满了 拒绝
                return 0
            end
            
          
             redis.call("ZADD",key,nowMs,requestId)
             redis.call('EXPIRE', key, ttlSec)
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
     * <p>
     * TODO [步骤3-2] 自己实现，要求一次 RTT 内原子完成「清理过期 member -&gt; 判限 -&gt; 入队 -&gt; 兜底过期」。
     * <p>
     * 实现要点：
     * <ul>
     *   <li>key 用 {@code RedisKey.concurrent(userId)}，now 用 {@code System.currentTimeMillis()}</li>
     *   <li>{@code redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
     *       LUA_ACQUIRE, RScript.ReturnType.LONG, List.of(key), ...)}</li>
     *   <li>阈值取自 {@code props.getLimit()} / {@code props.getWindowSeconds()} / {@code props.getKeyTtlSeconds()}
     *       （注意窗口要换算成毫秒）</li>
     *   <li>返回 1L 表示拿到额度；否则打 warn 日志带 userId/requestId 并返回 false</li>
     * </ul>
     *
     * @return true 放行；false 并发已满
     */
    public boolean tryAcquire(String userId, String requestId) {

        // 1. 准备 Key
        String key = RedisKey.concurrent(userId);

        // 2. 准备参数 (严格遵循注释要求：数字转 String，窗口转毫秒)
        long nowMs = System.currentTimeMillis();
        long windowMs = props.getWindowSeconds() * 1000L; // 【修复】秒转毫秒
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
     * <p>
     * TODO [步骤3-3] 自己实现：把本次的 requestId 从 ZSet 中 ZREM 掉。
     * <p>
     * 实现要点：
     * <ul>
     *   <li>{@code redissonClient.getScoredSortedSet(RedisKey.concurrent(userId), StringCodec.INSTANCE)}
     *       然后 {@code remove(requestId)}，ZREM 天然幂等，重复调用安全</li>
     *   <li>用 try/catch 包住，异常只打 error 日志并吞掉</li>
     * </ul>
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
     * <p>
     * 这个方法已经实现好了，可以直接用它来验证你的 tryAcquire / release 是否正确。
     */
    public int currentCount(String userId) {
        long minScore = System.currentTimeMillis() - props.getWindowSeconds() * 1000L;
        RScoredSortedSet<String> zset =
                redissonClient.getScoredSortedSet(RedisKey.concurrent(userId), StringCodec.INSTANCE);
        return zset.count(minScore, true, Double.POSITIVE_INFINITY, true);
    }
}
