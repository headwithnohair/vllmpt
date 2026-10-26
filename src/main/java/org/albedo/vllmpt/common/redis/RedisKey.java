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

    public static String usage(String userId,String yyyyMMdd) {

        return  USAGE_PREFIX + userId + ':' + yyyyMMdd;
    }

    /** 日额度的缓存，避免每个请求都查 MySQL；配额变更时删掉这个键即可 */
    public static final String QUOTA_LIMIT_PREFIX = "ai:quota:limit:";

    public static String quotaLimit(String userId) {
        return  QUOTA_LIMIT_PREFIX + userId ;
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

    /** 审批单号序号前缀：approval:no:{yyyyMMdd}，全业务共享，保证 instance_no 全局唯一 */
    public static final String APPROVAL_NO_PREFIX = "approval:no:";

    /**
     * 审批单号序号：approval:no:{yyyyMMdd}   String（计数器）
     * <p>
     * 不区分业务类型：单号格式为 AP + yyyyMMdd + 6 位序号，
     * 若按业务类型分别计数，不同业务会生成相同单号而撞上 uk_instance_no。
     * TTL 2 天。
     */
    public static String approvalNo(String yyyyMMdd) {
        return APPROVAL_NO_PREFIX + yyyyMMdd;
    }

    /** 审批提交幂等前缀 */
    public static final String APPROVAL_SUBMIT_IDEM_PREFIX = "approval:submit:idem:";
    /**
     * 审批提交幂等：approval:submit:idem:{userId}:{idempotencyKey}   String
     * <p>
     * 首次提交写入占位，成功后改成实例 ID；重复提交直接返回同一实例。TTL 5 分钟。
     */
    public static String approvalSubmitIdem(Long userId, String idempotencyKey) {
        return APPROVAL_SUBMIT_IDEM_PREFIX + userId + ':' + idempotencyKey;
    }

    /** 审批填表草稿前缀 */
    public static final String APPROVAL_DRAFT_PREFIX = "approval:draft:";

    /**
     * 审批填表草稿：approval:draft:{userId}:{bizType}:{sessionId}   String(JSON)
     * <p>
     * TTL 30 分钟；只存表单与缺失字段，不进入正式审批表。
     */
    public static String approvalDraft(Long userId, String bizType, String sessionId) {
        return APPROVAL_DRAFT_PREFIX + userId + ':' + bizType + ':' + sessionId;
    }

    /** 文件解析任务实时进度前缀（Hash） */
    public static final String FILE_TASK_PROGRESS_PREFIX = "ai:file:task:progress:";

    /**
     * 文件解析任务实时进度：ai:file:task:progress:{taskId}  Hash
     * <p>
     * 由消费者在每个阶段完成后写入，供 SSE 推送 / 前端轮询读取；
     * 建议字段：{@code status}（PENDING/RUNNING/SUCCESS/FAILED）、{@code stage}（当前阶段）、
     * {@code percent}（0-100）、{@code message}（展示文案/错误摘要）、{@code updatedAt}（毫秒时间戳）。
     * <p>
     * TTL 取配置 {@code app.file-task.progress-ttl-seconds}（默认 24h），兜底清理异常中断的残留 key。
     * <p>
     * 注意：消费/写入时使用 String 可读的 codec（如 {@code StringCodec.INSTANCE}），
     * 否则 redis-cli 里看到的是二进制乱码。
     */
    public static String fileTaskProgress(String taskId) {
        return FILE_TASK_PROGRESS_PREFIX + taskId;
    }
}
