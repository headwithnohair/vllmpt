package org.albedo.vllmpt.module.approval;

import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.approval.core.service.ApprovalNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalNumberGeneratorTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RedissonClient redisson;
    private RAtomicLong counter;
    private ApprovalNumberGenerator generator;
    private String today;

    @BeforeEach
    void setUp() {
        redisson = mock(RedissonClient.class);
        counter = mock(RAtomicLong.class);
        generator = new ApprovalNumberGenerator(redisson);
        today = LocalDate.now().format(DATE);
    }

    @Test
    void generatesDailyGlobalSequenceAcrossBizTypes() {
        when(redisson.getAtomicLong(RedisKey.approvalNo(today))).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(1L, 2L);

        assertEquals("AP" + today + "000001", generator.next("LEAVE"));
        assertEquals("AP" + today + "000002", generator.next("TRAVEL_EXPENSE"));
        verify(counter).expire(2, TimeUnit.DAYS);
    }

    @Test
    void fallsBackToRandomWhenRedisUnavailable() {
        when(redisson.getAtomicLong(anyString())).thenReturn(counter);
        when(counter.incrementAndGet()).thenThrow(new IllegalStateException("redis down"));

        String no = generator.next("LEAVE");

        assertEquals(16, no.length());
        assertTrue(no.startsWith("AP" + today));
        assertTrue(no.substring(10).matches("\\d{6}"));
    }

    @Test
    void blankBizTypeUsesSameDailyCounter() {
        when(redisson.getAtomicLong(RedisKey.approvalNo(today))).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(3L);

        assertEquals("AP" + today + "000003", generator.next(null));
    }
}
