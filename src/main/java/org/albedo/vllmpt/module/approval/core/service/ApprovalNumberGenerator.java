package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 审批单号生成器。
 * <p>
 * 单号格式：AP + yyyyMMdd + 6 位序号，例如 AP20261021000001。
 * 序号按日期在 Redis 中全局原子递增（不区分业务类型），保证 instance_no 全局唯一；
 * key 保留 2 天。Redis 不可用时降级为「日期 + 6 位随机数」，保证审批主链路不被缓存故障阻断，
 * 极端情况下的冲突由 {@code uk_instance_no} 唯一键兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String NO_PREFIX = "AP";
    private static final int SEQ_WIDTH = 6;
    private static final int COUNTER_TTL_DAYS = 2;

    private final RedissonClient redissonClient;

    public String next(String bizType) {
        String type = StrUtil.blankToDefault(bizType, "GENERAL").trim().toUpperCase(Locale.ROOT);
        String date = LocalDate.now().format(DATE_FORMAT);
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(RedisKey.approvalNo(date));
            long seq = counter.incrementAndGet();
            if (seq == 1) {
                counter.expire(COUNTER_TTL_DAYS, TimeUnit.DAYS);
            }
            return NO_PREFIX + date + String.format(Locale.ROOT, "%0" + SEQ_WIDTH + "d", seq);
        } catch (RuntimeException e) {
            log.warn("[审批] Redis 单号计数不可用，降级为随机单号: bizType={}, error={}",
                    type, e.getMessage());
            return NO_PREFIX + date + RandomUtil.randomNumbers(SEQ_WIDTH);
        }
    }
}
