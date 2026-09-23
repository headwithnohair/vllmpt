package org.albedo.vllmpt.common.redis;

/**
 * 集中管理 Redis key，避免前缀字符串散落在各处。
 */
public final class RedisKey {

    private RedisKey() {
    }
    /** 单用户当日已使用Token  前缀  String*/
    public static final String QUOTA_PREFIX = "ai:quota:";

    public static String quota(String userId,String yyyyMMdd) {

        return  QUOTA_PREFIX + userId + ':' + yyyyMMdd;
    }


    /** 该模型当日累计 token。用于"这个用户的额度花在哪个模型上"   Hash */
    public static final String USAGE_PREFIX = "ai:usage:";

    public static String USAGE(String userId,String yyyyMMdd) {

        return  USAGE_PREFIX + userId + ':' + yyyyMMdd;
    }

    /** 日额度的缓存，避免每个请求都查 MySQL；配额变更时删掉这个键即可 */
    public static final String QUOTA_lIMIT_PREFIX = "ai:quota:limit:";

    public static String quotalimit(String userId,String yyyyMMdd) {

        return  QUOTA_lIMIT_PREFIX + userId ;
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
