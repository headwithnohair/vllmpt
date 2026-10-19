package org.albedo.vllmpt.module.approval.core.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.approval.core.domain.dto.DecisionRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.TaskDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.TaskTodoVO;
import org.albedo.vllmpt.module.approval.core.service.ApprovalTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批任务接口：我的待办、任务详情、同意、拒绝。
 */
@RestController
@RequestMapping("/api/approval/task")
@RequiredArgsConstructor
public class ApprovalTaskController {

    private final ApprovalTaskService taskService;

    @GetMapping("/todo")
    public Result<List<TaskTodoVO>> todo(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.success(taskService.todo(limit));
    }

    @GetMapping("/{id}")
    public Result<TaskDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(taskService.detail(id));
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable("id") Long id,
                                @Valid @RequestBody(required = false) DecisionRequest request) {
        taskService.approve(id, request == null ? null : request.getComment());
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable("id") Long id,
                               @Valid @RequestBody(required = false) DecisionRequest request) {
        taskService.reject(id, request == null ? null : request.getComment());
        return Result.success();
    }
}
