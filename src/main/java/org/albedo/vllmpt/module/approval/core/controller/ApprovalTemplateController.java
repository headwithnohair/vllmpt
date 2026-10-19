package org.albedo.vllmpt.module.approval.core.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateCreateRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.TemplateDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.service.ApprovalTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批模板接口：创建、发布、详情、列表。
 * <p>
 * 模板发布后不可修改，调整流程时用同一个 templateKey 创建新版本。
 */
@RestController
@RequestMapping("/api/approval/template")
@RequiredArgsConstructor
public class ApprovalTemplateController {

    private final ApprovalTemplateService templateService;

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TemplateCreateRequest request) {
        return Result.success(templateService.create(request));
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable("id") Long id) {
        templateService.publish(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<TemplateDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(templateService.detail(id));
    }

    @GetMapping("/list")
    public Result<List<ApprovalTemplate>> list(
            @RequestParam(value = "bizType", required = false) String bizType) {
        return Result.success(templateService.list(bizType));
    }
}
