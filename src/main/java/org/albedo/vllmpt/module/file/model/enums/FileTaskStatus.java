package org.albedo.vllmpt.module.file.model.enums;

/**
 * 文件解析任务「台账状态」（MySQL ai_file_parse_task.status 列）。
 * <p>
 * 只承载起始态与两个终态；中间的阶段流转（解析中 / 分块中 / 向量化中）
 * 高频且短暂，只写 Redis 进度（{@link org.albedo.vllmpt.common.redis.RedisKey#fileTaskProgress(String)}），
 * 不落 MySQL，避免阶段更新打满数据库。
 */
public enum FileTaskStatus {

    /** 已创建台账，等待 / 正在 MQ 流水线处理 */
    PENDING,

    /** 全链路完成（分块已向量化入库） */
    SUCCESS,

    /** 失败终态（重试耗尽进入 DLQ，或超时判定失败） */
    FAILED
}
