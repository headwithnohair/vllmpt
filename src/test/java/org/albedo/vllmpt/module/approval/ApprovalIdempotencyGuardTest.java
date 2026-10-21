package org.albedo.vllmpt.module.approval;

import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.approval.core.service.ApprovalIdempotencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ApprovalIdempotencyGuardTest {

    private RedissonClient redisson;
    private RBucket<String> bucket;
    private ApprovalIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        redisson = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        guard = new ApprovalIdempotencyGuard(redisson);
    }

    @Test
    void blankKeySkipsIdempotency() {
        assertNull(guard.acquire(1L, null));
        assertNull(guard.acquire(1L, " "));
        verify(redisson, never()).getBucket(anyString());
    }

    @Test
    void firstRequestAcquiresPlaceholder() {
        when(redisson.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.trySet("PENDING", 5, TimeUnit.MINUTES)).thenReturn(true);

        assertNull(guard.acquire(7L, "key-1"));

        verify(redisson).getBucket(RedisKey.approvalSubmitIdem(7L, "key-1"));
        verify(bucket).trySet("PENDING", 5, TimeUnit.MINUTES);
    }

    @Test
    void duplicateReturnsExistingInstance() {
        when(redisson.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.trySet("PENDING", 5, TimeUnit.MINUTES)).thenReturn(false);
        when(bucket.get()).thenReturn("88");

        assertEquals(88L, guard.acquire(7L, "key-1"));
    }

    @Test
    void inFlightDuplicateRejected() {
        when(redisson.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.trySet("PENDING", 5, TimeUnit.MINUTES)).thenReturn(false);
        when(bucket.get()).thenReturn("PENDING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.acquire(7L, "key-1"));
        assertEquals(429, ex.getCode());
    }

    @Test
    void redisFailureDegradesToNoGuard() {
        when(redisson.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.trySet(anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new IllegalStateException("down"));

        assertNull(guard.acquire(7L, "key-1"));
    }

    @Test
    void completeWritesInstanceId() {
        when(redisson.<String>getBucket(anyString())).thenReturn(bucket);

        guard.complete(7L, "key-1", 99L);

        verify(bucket).set("99", 5, TimeUnit.MINUTES);
    }

    @Test
    void completeAndReleaseSkipBlankKey() {
        guard.complete(7L, "", 99L);
        guard.release(7L, null);
        verify(redisson, never()).getBucket(anyString());
    }
}
