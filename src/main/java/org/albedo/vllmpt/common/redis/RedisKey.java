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
}
