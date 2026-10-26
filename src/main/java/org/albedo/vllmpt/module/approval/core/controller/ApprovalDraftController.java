package org.albedo.vllmpt.module.approval.core.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftVO;
import org.albedo.vllmpt.module.approval.core.service.ApprovalDraftService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批填表草稿接口：保存/合并、查询当前草稿、删除。
 */
@RestController
@RequestMapping("/api/approval/draft")
@RequiredArgsConstructor
public class ApprovalDraftController {

    private final ApprovalDraftService draftService;

    @PostMapping
    public Result<ApprovalDraftVO> save(@Valid @RequestBody ApprovalDraftRequest request) {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(draftService.save(userId, request));
    }

    @GetMapping("/current")
    public Result<ApprovalDraftVO> current(@RequestParam("bizType") String bizType,
                                           @RequestParam(value = "sessionId", required = false) String sessionId) {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(draftService.get(userId, bizType, sessionId));
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam("bizType") String bizType,
                               @RequestParam(value = "sessionId", required = false) String sessionId) {
        Long userId = UserContextHolder.requireUserId();
        draftService.delete(userId, bizType, sessionId);
        return Result.success();
    }
}
