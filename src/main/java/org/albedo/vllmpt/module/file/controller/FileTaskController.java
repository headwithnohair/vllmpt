package org.albedo.vllmpt.module.file.controller;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.albedo.vllmpt.module.file.model.dto.FileSubmitRequest;
import org.albedo.vllmpt.module.file.model.entity.FileParseTask;
import org.albedo.vllmpt.module.file.mq.FileParseMessage;
import org.albedo.vllmpt.module.file.mq.FileParseProducer;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大文件解析任务接口（场景6配套）：提交入口 + 台账查询。
 * <p>
 * 这里查的是 MySQL 台账（最终状态）；<b>实时进度</b>（stage/percent）在 Redis，
 * 由消费者写入 + SSE/轮询推送，接口由对应实现方自行暴露。
 */
@RestController
@RequestMapping("/api/file/task")
@RequiredArgsConstructor
@Slf4j
public class FileTaskController {

    /** 预签名直传的临时桶（PUT 上传落点） */
    private static final String TEMP_BUCKET = "vllmpt-temp";

    /** 正式数据桶（归档后源文件 + 解析产物都在这里） */
    private static final String DATA_BUCKET = "vllmpt-data";

    private final FileParseTaskService fileParseTaskService;
    private final MinioService minioService;
    private final FileParseProducer fileParseProducer;

    /**
     * 提交异步解析任务（场景6 入口，替代同步版 /api/indexAction/file）。
     * <p>
     * 流程：校验 temp 文件 → 归档到数据桶 → 建台账 → 发「解析消息」+「超时检查延迟消息」，
     * <b>立即返回 taskId</b>，后续由消费者流水线异步处理。
     */
    @PostMapping("/submit")
    public Result<String> submit(@RequestBody FileSubmitRequest request) {
        if (StrUtil.isBlank(request.getObjectName())) {
            throw new BusinessException(400, "objectName 不能为空");
        }

        // 1) 校验 temp 桶文件存在，并回填 mimeType / fileSize / etag（stat 不到时 mimeType 为 null）
        FileUploadDTO dto = new FileUploadDTO();
        dto.setObjectName(request.getObjectName());
        minioService.FileExistCheck(dto, TEMP_BUCKET);
        if (dto.getMimeType() == null) {
            throw new BusinessException(400, "文件不存在或尚未完成上传: " + request.getObjectName());
        }
        // etag 校验：请求带了才比对（防止上传中断产生不完整文件）
        if (StrUtil.isNotBlank(request.getEtag()) && !request.getEtag().equals(dto.getEtag())) {
            throw new BusinessException(400, "文件校验失败（etag 不一致），请重新上传");
        }

        // 2) 归档 temp → data（同区拷贝很快；让消费者只面对正式数据桶）
        String finalObjectName = minioService.FileChangeBucket(dto, TEMP_BUCKET, DATA_BUCKET);

        // 3) 建台账（taskId 是后续一切流转的关联键）
        String fileName = StrUtil.blankToDefault(request.getFileName(), request.getObjectName());
        String taskId = fileParseTaskService.createTask(
                fileName, DATA_BUCKET, finalObjectName, dto.getFileSize(), dto.getMimeType(), request.getUserId());

        // 4) 发消息：解析消息（级联起点）+ 超时检查延迟消息（同生命周期探针）
        FileParseMessage message = new FileParseMessage();
        message.setTaskId(taskId);
        message.setFileName(fileName);
        message.setBucket(DATA_BUCKET);
        message.setObjectName(finalObjectName);
        message.setFileSize(dto.getFileSize());
        message.setMimeType(dto.getMimeType());
        message.setUserId(request.getUserId());
        try {
            fileParseProducer.sendParseStage(message);
            fileParseProducer.sendTimeoutCheck(message);
        } catch (Exception e) {
            // 消息没发出去 = 任务永远不会被处理：标失败，避免残留 PENDING 幽灵任务
            fileParseTaskService.markFailed(taskId, "消息发送失败: " + e.getMessage());
            throw e;
        }

        log.info("[提交任务] taskId={}, file={}, size={} bytes", taskId, fileName, dto.getFileSize());
        return Result.success(taskId);
    }

    /**
     * 任务详情（前端轮询兜底 / 页面刷新后恢复状态）。
     */
    @GetMapping("/{taskId}")
    public Result<FileParseTask> getTask(@PathVariable("taskId") String taskId) {
        FileParseTask task = fileParseTaskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在: " + taskId);
        }
        return Result.success(task);
    }

    /**
     * 最近任务列表。
     *
     * @param userId 可选，不传返回全部（dev 联调方便）
     * @param limit  条数，默认 20，内部钳制到 [1, 100]
     */
    @GetMapping("/list")
    public Result<List<FileParseTask>> listTasks(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(fileParseTaskService.listRecent(userId, limit));
    }
}
