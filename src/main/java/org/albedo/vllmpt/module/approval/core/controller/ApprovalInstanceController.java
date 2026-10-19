package org.albedo.vllmpt.module.approval.core.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.approval.core.domain.dto.CancelRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.SubmitInstanceRequest;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.service.ApprovalInstanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批实例接口：提交、详情、我的申请、撤回。
 */
@RestController
@RequestMapping("/api/approval/instance")
@RequiredArgsConstructor
public class ApprovalInstanceController {

    private final ApprovalInstanceService instanceService;

    @PostMapping("/submit")
    public Result<Long> submit(@Valid @RequestBody SubmitInstanceRequest request) {
        return Result.success(instanceService.submit(request));
    }

    @GetMapping("/{id}")
    public Result<InstanceDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(instanceService.detail(id));
    }

    @GetMapping("/my")
    public Result<List<ApprovalInstance>> my(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(instanceService.my(status, limit));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id,
                               @RequestBody(required = false) CancelRequest request) {
        instanceService.cancel(id, request == null ? null : request.getReason());
        return Result.success();
    }
}
