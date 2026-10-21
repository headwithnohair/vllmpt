package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 审批提交幂等守卫。
 * <p>
 * 语义：
 * <ul>
 *   <li>首次提交：写入占位值，返回 null，调用方继续创建实例；</li>
 *   <li>同一 key 已完成：返回首次创建的实例 ID，调用方直接返回该 ID；</li>
 *   <li>同一 key 正在处理：抛 429，避免并发重复建单；</li>
 *   <li>Redis 不可用：跳过幂等校验并告警，审批主链路继续可用。</li>
 * </ul>
 * 幂等键保留 5 分钟，key 结构见 {@link RedisKey#approvalSubmitIdem(Long, String)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalIdempotencyGuard {

    private static final String PENDING = "PENDING";
    private static final long TTL_MINUTES = 5;

    private final RedissonClient redissonClient;

    /**
     * 占位并读取已有实例。
     *
     * @return 已完成提交的实例 ID；首次提交返回 null
     */
    public Long acquire(Long userId, String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            return null;
        }
        try {
            RBucket<String> bucket = bucket(userId, idempotencyKey);
            if (bucket.trySet(PENDING, TTL_MINUTES, TimeUnit.MINUTES)) {
                return null;
            }
            String value = bucket.get();
            if (StrUtil.isBlank(value)) {
                // 占位刚被回滚释放，按首次提交处理
                return null;
            }
            if (PENDING.equals(value)) {
                throw new BusinessException(429, "审批处理中，请稍后重试");
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new BusinessException(429, "审批处理中，请稍后重试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[审批] 幂等键不可用，跳过幂等校验: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 提交成功后把占位值替换为实例 ID，重复请求可直接拿到同一实例。
     */
    public void complete(Long userId, String idempotencyKey, Long instanceId) {
        if (StrUtil.isBlank(idempotencyKey) || instanceId == null) {
            return;
        }
        try {
            bucket(userId, idempotencyKey).set(String.valueOf(instanceId), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("[审批] 幂等键写入失败: userId={}, instanceId={}, error={}",
                    userId, instanceId, e.getMessage());
        }
    }

    /**
     * 提交失败或事务回滚时释放占位，允许用户重试。
     */
    public void release(Long userId, String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            return;
        }
        try {
            bucket(userId, idempotencyKey).delete();
        } catch (RuntimeException e) {
            log.warn("[审批] 幂等键释放失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private RBucket<String> bucket(Long userId, String idempotencyKey) {
        return redissonClient.getBucket(RedisKey.approvalSubmitIdem(userId, idempotencyKey));
    }
}
