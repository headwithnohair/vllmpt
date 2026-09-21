package org.albedo.vllmpt.common.redis;

/**
 * 集中管理 Redis key，避免前缀字符串散落在各处。
 */
public final class RedisKey {

    private RedisKey() {
    }

    /** 单用户并发推理任务 ZSet 前缀 */
    public static final String CONCURRENT_PREFIX = "ai:concurrent:";

    /**
     * 单用户并发推理任务 ZSet：ai:concurrent:{userId}
     */
    public static String concurrent(String userId) {
        return CONCURRENT_PREFIX + userId;
    }

    /** 会话互斥锁前缀 */
    public static final String SESSION_LOCK_PREFIX = "ai:session:lock:";

    /**
     * 会话互斥锁：ai:session:lock:{sessionId}
     * <p>
     * 底层是 Redisson RLock 的 Hash 结构，field = {@code <clientUuid:threadId>}，value = 重入次数。
     */
    public static String sessionLock(String sessionId) {
        return SESSION_LOCK_PREFIX + sessionId;
    }
}
