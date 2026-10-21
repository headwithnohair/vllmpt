package org.albedo.vllmpt.module.approval.core.controller;

import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.approval.core.domain.dto.CcItemVO;
import org.albedo.vllmpt.module.approval.core.service.ApprovalCcService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批抄送接口：抄送我的、标记已读。
 */
@RestController
@RequestMapping("/api/approval/cc")
@RequiredArgsConstructor
public class ApprovalCcController {

    private final ApprovalCcService ccService;

    @GetMapping("/my")
    public Result<List<CcItemVO>> my(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(ccService.my(limit));
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable("id") Long id) {
        ccService.markRead(id);
        return Result.success();
    }
}
