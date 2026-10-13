package org.albedo.vllmpt.module.file.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.file.model.entity.FileParseTask;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大文件解析任务查询接口（场景6配套）。
 * <p>
 * 这里查的是 MySQL 台账（最终状态）；<b>实时进度</b>（stage/percent）在 Redis，
 * 由消费者写入 + SSE/轮询推送，接口由对应实现方自行暴露。
 */
@RestController
@RequestMapping("/api/file/task")
@RequiredArgsConstructor
@Slf4j
public class FileTaskController {

    private final FileParseTaskService fileParseTaskService;

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
