package org.albedo.vllmpt.module.approval.biz.leave.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.biz.leave.domain.dto.LeaveDetailVO;
import org.albedo.vllmpt.module.approval.biz.leave.domain.dto.LeaveSubmitRequest;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveBalance;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.LeaveRequestMapper;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveBalanceService;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveBizHandler;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假业务接口：提交、详情、我的请假、撤回、余额查询。
 * <p>
 * 全部委托通用审批服务，业务差异由 {@link LeaveBizHandler} 承担。
 */
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final ApprovalInstanceService instanceService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveBalanceService balanceService;

    /**
     * 提交请假：内部转换为通用提交请求（templateKey=leave）。
     */
    @PostMapping("/request")
    public Result<Long> submit(@Valid @RequestBody LeaveSubmitRequest request) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("leaveType", request.getLeaveType());
        form.put("startTime", request.getStartTime());
        form.put("endTime", request.getEndTime());
        form.put("reason", request.getReason());
        if (request.getAttachmentUrls() != null) {
            form.put("attachmentUrls", request.getAttachmentUrls());
        }

        SubmitInstanceRequest submit = new SubmitInstanceRequest();
        submit.setTemplateKey(LeaveBizHandler.TEMPLATE_KEY);
        submit.setBizType(LeaveBizHandler.BIZ_TYPE);
        submit.setTitle(StrUtil.blankToDefault(request.getTitle(), "请假申请"));
        submit.setFormData(form);
        submit.setIdempotencyKey(request.getIdempotencyKey());
        return Result.success(instanceService.submit(submit));
    }

    @GetMapping("/request/{instanceId}")
    public Result<LeaveDetailVO> detail(@PathVariable("instanceId") Long instanceId) {
        InstanceDetailVO approval = instanceService.detail(instanceId);
        LeaveRequest leave = leaveRequestMapper.selectOne(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApprovalInstanceId, instanceId)
                .last("LIMIT 1"));
        return Result.success(new LeaveDetailVO(approval, leave));
    }

    @GetMapping("/request/my")
    public Result<List<ApprovalInstance>> my(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(instanceService.my(LeaveBizHandler.BIZ_TYPE, status, limit));
    }

    @PostMapping("/request/{instanceId}/cancel")
    public Result<Void> cancel(@PathVariable("instanceId") Long instanceId,
                               @RequestBody(required = false) CancelRequest request) {
        instanceService.cancel(instanceId, request == null ? null : request.getReason());
        return Result.success();
    }

    /**
     * 当前用户当年的假期余额（年假/调休）。
     */
    @GetMapping("/balance")
    public Result<List<LeaveBalance>> balance() {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(balanceService.list(userId, LocalDate.now().getYear()));
    }
}
