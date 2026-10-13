package org.albedo.vllmpt.module.file.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.file.mapper.FileParseTaskMapper;
import org.albedo.vllmpt.module.file.model.entity.FileParseTask;
import org.albedo.vllmpt.module.file.model.enums.FileTaskStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 大文件解析任务台账服务（MySQL 侧，场景6）。
 * <p>
 * 只负责「台账」的增改查：创建任务、（终态）标记成功/失败、查询。
 * <b>实时进度不在这里</b>——阶段流转（解析中/分块中/向量化中）由消费者写
 * Redis（{@link org.albedo.vllmpt.common.redis.RedisKey#fileTaskProgress(String)}）并推送前端。
 * <p>
 * 终态更新采用「仅当 status=PENDING 时可更新」的条件更新，天然幂等：
 * 重复消费、DLQ 迟到消息都不会把已完结的任务改花。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseTaskService {

    private final FileParseTaskMapper fileParseTaskMapper;

    /**
     * 创建任务台账（生产者接口在文件上传完成后调用）。
     *
     * @return 生成的 taskId（UUID），需要随 MQ 消息流转
     */
    public String createTask(String fileName, String bucket, String objectName,
                             long fileSize, String mimeType, Long userId) {
        String taskId = UUID.randomUUID().toString();
        FileParseTask task = new FileParseTask();
        task.setTaskId(taskId);
        task.setUserId(userId == null ? 0L : userId);
        task.setFileName(fileName);
        task.setBucket(bucket);
        task.setObjectName(objectName);
        task.setFileSize(fileSize);
        task.setMimeType(mimeType);
        task.setStatus(FileTaskStatus.PENDING.name());
        task.setChunkCount(0);
        fileParseTaskMapper.insert(task);
        log.info("任务台账已创建: taskId={}, file={}, size={}", taskId, fileName, fileSize);
        return taskId;
    }

    /**
     * 标记任务成功（全部阶段完成时调用）。
     *
     * @param chunkCount 分块数量，可为 null（不回填）
     * @return true=本次确实完成流转；false=任务不存在或已是终态（重复消息，正常现象）
     */
    public boolean markSuccess(String taskId, Integer chunkCount) {
        LambdaUpdateWrapper<FileParseTask> wrapper = new LambdaUpdateWrapper<FileParseTask>()
                .eq(FileParseTask::getTaskId, taskId)
                .eq(FileParseTask::getStatus, FileTaskStatus.PENDING.name())
                .set(FileParseTask::getStatus, FileTaskStatus.SUCCESS.name())
                .set(chunkCount != null, FileParseTask::getChunkCount, chunkCount);
        boolean updated = fileParseTaskMapper.update(null, wrapper) > 0;
        if (!updated) {
            log.debug("markSuccess 未生效（任务不存在或已是终态）: taskId={}", taskId);
        }
        return updated;
    }

    /**
     * 标记任务失败（本地重试耗尽 / DLQ 消费者 / 超时检查时调用）。
     *
     * @param errorMsg 失败原因，超 1000 字符会截断（列宽 1024）
     * @return true=本次确实完成流转；false=任务不存在或已是终态
     */
    public boolean markFailed(String taskId, String errorMsg) {
        LambdaUpdateWrapper<FileParseTask> wrapper = new LambdaUpdateWrapper<FileParseTask>()
                .eq(FileParseTask::getTaskId, taskId)
                .eq(FileParseTask::getStatus, FileTaskStatus.PENDING.name())
                .set(FileParseTask::getStatus, FileTaskStatus.FAILED.name())
                .set(FileParseTask::getErrorMsg, StrUtil.maxLength(errorMsg, 1000));
        boolean updated = fileParseTaskMapper.update(null, wrapper) > 0;
        if (!updated) {
            log.debug("markFailed 未生效（任务不存在或已是终态）: taskId={}", taskId);
        }
        return updated;
    }

    /** 按 taskId 查台账，不存在返回 null。 */
    public FileParseTask getByTaskId(String taskId) {
        return fileParseTaskMapper.selectOne(new LambdaQueryWrapper<FileParseTask>()
                .eq(FileParseTask::getTaskId, taskId));
    }

    /**
     * 最近任务列表（前端任务中心）。
     *
     * @param userId 用户 ID，null=不过滤（dev 联调用）
     * @param limit  条数，内部钳制到 [1, 100]
     */
    public List<FileParseTask> listRecent(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return fileParseTaskMapper.selectList(new LambdaQueryWrapper<FileParseTask>()
                .eq(userId != null, FileParseTask::getUserId, userId)
                .orderByDesc(FileParseTask::getCreatedAt)
                .last("LIMIT " + safeLimit));
    }
}
