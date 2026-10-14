package org.albedo.vllmpt.module.file.mq;

/**
 * 场景6 消息队列拓扑常量（RocketMQ）。
 * <p>
 * 拓扑设计（每阶段独立 Topic，互不干扰、可独立扩容）：
 * <pre>
 *   FILE_PARSE_TOPIC（解析阶段）
 *        │  消费组 file-parse-consumer-group；失败重试耗尽 → %DLQ%file-parse-consumer-group
 *        │  解析完成 → 发送下一阶段消息
 *        ▼
 *   FILE_EMBED_TOPIC（向量化阶段）
 *        │  消费组 file-embed-consumer-group；失败重试耗尽 → %DLQ%file-embed-consumer-group
 *        ▼
 *   台账 markSuccess（MySQL）+ 进度 success（Redis）
 *
 *   FILE_TIMEOUT_TOPIC（延迟消息：超时检查）
 *        消费组 file-timeout-consumer-group；发送时设置延迟级别，到点后消费比对 Redis updatedAt
 * </pre>
 * 类注释里的两个考点说明：
 * <ul>
 *   <li><b>Tag 过滤</b>：当前方案按「阶段」拆 Topic，所以消费端不用 Tag。
 *       如果想练「单 Topic + 多 Tag」的方案：发送时用 {@code topic:tag} 语法
 *       （如 {@code "FILE_PIPELINE_TOPIC:PARSE"}），消费端注解写
 *       {@code selectorExpression = "PARSE || EMBED"}，两种设计可以对比掂量。</li>
 *   <li><b>DLQ 命名</b>：死信 Topic 固定为 {@code %DLQ%消费者组名}，由 broker 在重试耗尽后
 *       自动创建，不需要（也不应该）手动声明。</li>
 * </ul>
 */
public final class MqConst {

    private MqConst() {
    }

    // ==================== Topic ====================

    /** 解析阶段 Topic */
    public static final String TOPIC_FILE_PARSE = "FILE_PARSE_TOPIC";

    /** 向量化阶段 Topic */
    public static final String TOPIC_FILE_EMBED = "FILE_EMBED_TOPIC";

    /** 超时检查 Topic（延迟消息专用） */
    public static final String TOPIC_FILE_TIMEOUT = "FILE_TIMEOUT_TOPIC";

    // ==================== 消费组 ====================

    /** 解析阶段消费组 */
    public static final String GROUP_PARSE = "file-parse-consumer-group";

    /** 向量化阶段消费组 */
    public static final String GROUP_EMBED = "file-embed-consumer-group";

    /** 超时检查消费组 */
    public static final String GROUP_TIMEOUT = "file-timeout-consumer-group";

    /** 死信兜底消费组（监听 %DLQ% 必须用「新」消费组，不能复用原组） */
    public static final String GROUP_DLQ = "file-dlq-consumer-group";

    // ==================== 死信 Topic（broker 自动创建） ====================

    /** 解析阶段死信 Topic：%DLQ%file-parse-consumer-group */
    public static final String TOPIC_DLQ_PARSE = "%DLQ%" + GROUP_PARSE;

    /** 向量化阶段死信 Topic：%DLQ%file-embed-consumer-group */
    public static final String TOPIC_DLQ_EMBED = "%DLQ%" + GROUP_EMBED;

    // ==================== 延迟级别（setDelayTimeLevel 用，完整对照见文档 3.5） ====================

    /** 延迟 30 分钟（level 16，恰好等于默认超时阈值，适合做"刚好到点"的检查） */
    public static final int DELAY_LEVEL_30M = 16;

    /** 延迟 1 小时（level 17）：超时检查推荐用它，到点后比对 Redis updatedAt 是否超过阈值 */
    public static final int DELAY_LEVEL_1H = 17;

    /** 延迟 2 小时（level 18） */
    public static final int DELAY_LEVEL_2H = 18;

    // ==================== 消费重试 ====================

    /**
     * 消费失败最大重试次数（并发消费）。
     * <p>
     * 重试耗尽后消息自动进入 {@code %DLQ%{consumerGroup}}。
     * 开发期设小一点（3 次）便于观察死信链路；生产按业务容忍度调整。
     */
    public static final int MAX_RECONSUME_TIMES = 3;
}
