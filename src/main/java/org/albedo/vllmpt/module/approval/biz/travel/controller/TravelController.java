package org.albedo.vllmpt.module.approval.biz.travel.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.approval.biz.travel.domain.dto.TravelDetailVO;
import org.albedo.vllmpt.module.approval.biz.travel.domain.dto.TravelSubmitRequest;
import org.albedo.vllmpt.module.approval.biz.travel.domain.entity.TravelExpense;
import org.albedo.vllmpt.module.approval.biz.travel.mapper.TravelExpenseMapper;
import org.albedo.vllmpt.module.approval.biz.travel.service.TravelBizHandler;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 差旅费业务接口：提交、详情、我的差旅、撤回。
 */
@RestController
@RequestMapping("/api/travel")
@RequiredArgsConstructor
public class TravelController {

    private final ApprovalInstanceService instanceService;
    private final TravelExpenseMapper travelExpenseMapper;

    @PostMapping("/request")
    public Result<Long> submit(@Valid @RequestBody TravelSubmitRequest request) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("destination", request.getDestination());
        form.put("startDate", request.getStartDate());
        form.put("endDate", request.getEndDate());
        form.put("estimatedAmount", request.getEstimatedAmount());
        form.put("currency", StrUtil.blankToDefault(request.getCurrency(), "CNY"));
        form.put("costCenter", request.getCostCenter());
        form.put("reason", request.getReason());
        if (request.getInvoiceUrls() != null) {
            form.put("invoiceUrls", request.getInvoiceUrls());
        }

        SubmitInstanceRequest submit = new SubmitInstanceRequest();
        submit.setTemplateKey(TravelBizHandler.TEMPLATE_KEY);
        submit.setBizType(TravelBizHandler.BIZ_TYPE);
        submit.setTitle(StrUtil.blankToDefault(request.getTitle(), "差旅费申请"));
        submit.setFormData(form);
        submit.setIdempotencyKey(request.getIdempotencyKey());
        return Result.success(instanceService.submit(submit));
    }

    @GetMapping("/request/{instanceId}")
    public Result<TravelDetailVO> detail(@PathVariable("instanceId") Long instanceId) {
        InstanceDetailVO approval = instanceService.detail(instanceId);
        TravelExpense travel = travelExpenseMapper.selectOne(Wrappers.<TravelExpense>lambdaQuery()
                .eq(TravelExpense::getApprovalInstanceId, instanceId)
                .last("LIMIT 1"));
        return Result.success(new TravelDetailVO(approval, travel));
    }

    @GetMapping("/request/my")
    public Result<List<ApprovalInstance>> my(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(instanceService.my(TravelBizHandler.BIZ_TYPE, status, limit));
    }

    @PostMapping("/request/{instanceId}/cancel")
    public Result<Void> cancel(@PathVariable("instanceId") Long instanceId,
                               @RequestBody(required = false) CancelRequest request) {
        instanceService.cancel(instanceId, request == null ? null : request.getReason());
        return Result.success();
    }
}
