package org.albedo.vllmpt.module.file.mq;

import lombok.Data;

/**
 * 大文件解析流水线的消息体（场景6），在三个阶段间流转：
 * <pre>
 *   submit 接口 → 解析消费者 → 向量化消费者 → （DLQ / 超时检查）
 * </pre>
 * 设计原则（考点：消息体最小化）：
 * <ul>
 *   <li>消息里只放「定位文件需要的元数据」，不放文件内容、不放进度——进度以 Redis 为准；</li>
 *   <li>{@code taskId} 是唯一业务主键，台账（MySQL）与进度（Redis）都靠它关联；</li>
 *   <li>{@code parsedObjectName} 由解析阶段写入，向量化阶段读取（解析产物的 MinIO 对象名）。</li>
 * </ul>
 * 序列化：rocketmq-spring 默认用 Jackson 将本对象序列化为 JSON 投递，消费端自动反序列化。
 */
@Data
public class FileParseMessage {

    /** 任务 ID（UUID，submit 时生成，贯穿台账/进度/消息） */
    private String taskId;

    /** 原始文件名（展示、日志用） */
    private String fileName;

    /** MinIO 桶名（解析阶段读源文件用；解析产物固定放 vllmpt-data） */
    private String bucket;

    /** MinIO 对象名（归档后的 finalObjectName） */
    private String objectName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型（消费者据此选择解析器） */
    private String mimeType;

    /** 用户 ID（0=未登录/系统任务） */
    private Long userId;

    // ==================== 阶段间传递的补充字段 ====================

    /**
     * 解析产物（纯文本）在 MinIO 中的对象名，如 {@code parsed/{taskId}.txt}。
     * <p>解析阶段写进度 30% 时一并填充，向量化阶段用它拉取文本。
     */
    private String parsedObjectName;
}
