package org.albedo.vllmpt.module.approval.biz.leave.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.LeaveRequestMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalBizHandler;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假业务插件。
 * <p>
 * 校验规则：
 * <ul>
 *   <li>时间格式、结束时间晚于开始时间、不能请过去的假、不支持跨年；</li>
 *   <li>工作日天数必须大于 0（节假日/周末自动扣除）；</li>
 *   <li>年假/调休校验余额；病假超过 1 天必须有证明；</li>
 *   <li>与本人 RUNNING/APPROVED 的请假时间重叠则拒绝。</li>
 * </ul>
 * 余额锁定/结算在业务记录事务内完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveBizHandler implements ApprovalBizHandler {

    public static final String BIZ_TYPE = "LEAVE";
    public static final String TEMPLATE_KEY = "leave";

    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveCalendarService calendarService;
    private final LeaveBalanceService balanceService;
    private final ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public ValidationResult validate(ApprovalForm form, ApprovalContext ctx) {
        List<String> errors = new ArrayList<>();
        LeaveType type = LeaveType.from(form.getString("leaveType"));
        if (type == null) {
            errors.add("请假类型不合法，可选 ANNUAL/SICK/PERSONAL/COMP_TIME");
        }

        LocalDateTime start = parseTime(form.getString("startTime"));
        LocalDateTime end = parseTime(form.getString("endTime"));
        if (start == null) {
            errors.add("开始时间格式不正确，应为 yyyy-MM-dd HH:mm");
        }
        if (end == null) {
            errors.add("结束时间格式不正确，应为 yyyy-MM-dd HH:mm");
        }

        BigDecimal days = BigDecimal.ZERO;
        if (start != null && end != null) {
            if (!end.isAfter(start)) {
                errors.add("结束时间必须晚于开始时间");
            } else {
                if (start.toLocalDate().isBefore(LocalDate.now())) {
                    errors.add("不能请过去的假");
                }
                if (start.getYear() != end.getYear()) {
                    errors.add("暂不支持跨年请假，请拆分为两单");
                }
                days = calendarService.calculateDays(start, end);
                if (days.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("请假区间内没有工作日");
                }
            }
        }

        String reason = StrUtil.trim(form.getString("reason"));
        if (StrUtil.isBlank(reason)) {
            errors.add("请假事由不能为空");
        } else if (reason.length() > 500) {
            errors.add("请假事由不能超过 500 字");
        }

        List<String> attachments = parseAttachments(form.get("attachmentUrls"));
        if (type == LeaveType.SICK && days.compareTo(BigDecimal.ONE) > 0 && attachments.isEmpty()) {
            errors.add("病假超过 1 天需上传证明材料");
        }

        if (type != null && type.balanceRequired()
                && start != null && days.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal available = balanceService.available(ctx.applicantId(), type, start.getYear());
            if (available.compareTo(days) < 0) {
                errors.add(type.label() + "余额不足，当前可用 " + strip(available) + " 天");
            }
        }

        if (start != null && end != null && end.isAfter(start)) {
            LeaveRequest overlap = findOverlap(ctx.applicantId(), start, end);
            if (overlap != null) {
                errors.add("与已有请假时间重叠：" + overlap.getStartTime() + " ~ " + overlap.getEndTime());
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    @Override
    public Long createBizRecord(ApprovalForm form, ApprovalContext ctx) {
        LeaveType type = requireType(form);
        LocalDateTime start = requireTime(form.getString("startTime"), "开始时间");
        LocalDateTime end = requireTime(form.getString("endTime"), "结束时间");
        BigDecimal days = calendarService.calculateDays(start, end);
        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "请假区间内没有工作日");
        }

        // 先锁余额，失败直接拒绝；后续实例创建失败时随事务回滚
        if (type.balanceRequired()) {
            balanceService.lock(ctx.applicantId(), type, days, start.getYear());
        }

        LocalDateTime now = LocalDateTime.now();
        LeaveRequest request = new LeaveRequest();
        request.setApplicantId(ctx.applicantId());
        request.setLeaveType(type.name());
        request.setStartTime(start);
        request.setEndTime(end);
        request.setLeaveDays(days);
        request.setReason(StrUtil.trim(form.getString("reason")));
        request.setAttachmentUrls(writeJson(parseAttachments(form.get("attachmentUrls"))));
        request.setApprovalStatus(ApprovalInstanceStatus.RUNNING.name());
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        leaveRequestMapper.insert(request);
        log.info("[请假] 创建业务记录: id={}, applicant={}, type={}, days={}",
                request.getId(), ctx.applicantId(), type, days);
        return request.getId();
    }

    @Override
    public void bindInstance(Long bizId, Long instanceId) {
        leaveRequestMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, bizId)
                .set(LeaveRequest::getApprovalInstanceId, instanceId)
                .set(LeaveRequest::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public Map<String, Object> extractFacts(ApprovalForm form, ApprovalContext ctx) {
        Map<String, Object> facts = new LinkedHashMap<>();
        LeaveType type = LeaveType.from(form.getString("leaveType"));
        LocalDateTime start = parseTime(form.getString("startTime"));
        LocalDateTime end = parseTime(form.getString("endTime"));
        if (type != null) {
            facts.put("leaveType", type.name());
            facts.put("leaveTypeLabel", type.label());
        }
        if (start != null && end != null) {
            facts.put("startTime", start.toString());
            facts.put("endTime", end.toString());
            BigDecimal days = calendarService.calculateDays(start, end);
            facts.put("leaveDays", days);
            if (type != null && type.balanceRequired()) {
                facts.put("balanceAvailable",
                        balanceService.available(ctx.applicantId(), type, start.getYear()));
            }
        }
        facts.put("hasAttachment", !parseAttachments(form.get("attachmentUrls")).isEmpty());
        return facts;
    }

    @Override
    public Object loadBizDetail(ApprovalInstance instance) {
        return findByInstance(instance);
    }

    @Override
    public void applyResult(ApprovalInstance instance, ApprovalInstanceStatus result) {
        LeaveRequest request = findByInstance(instance);
        if (request == null) {
            log.warn("[请假] 未找到业务记录，跳过结果回调: instanceId={}", instance.getId());
            return;
        }
        if (result.name().equals(request.getApprovalStatus())) {
            return;
        }

        LeaveType type = LeaveType.from(request.getLeaveType());
        if (type != null && type.balanceRequired() && request.getStartTime() != null) {
            int year = request.getStartTime().getYear();
            BigDecimal days = request.getLeaveDays() == null ? BigDecimal.ZERO : request.getLeaveDays();
            if (result == ApprovalInstanceStatus.APPROVED) {
                if (!balanceService.approve(request.getApplicantId(), type, days, year)) {
                    throw new BusinessException(500, "假期余额结算失败，请联系管理员");
                }
            } else if (result == ApprovalInstanceStatus.REJECTED
                    || result == ApprovalInstanceStatus.CANCELED
                    || result == ApprovalInstanceStatus.TERMINATED) {
                balanceService.release(request.getApplicantId(), type, days, year);
            }
        }

        leaveRequestMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, request.getId())
                .set(LeaveRequest::getApprovalStatus, result.name())
                .set(LeaveRequest::getUpdatedAt, LocalDateTime.now()));
        log.info("[请假] 结果回调: id={}, instanceId={}, status={}",
                request.getId(), instance.getId(), result);
    }

    @Override
    public void onSubmitted(ApprovalInstance instance) {
        log.info("[请假] 已提交: instanceId={}, no={}", instance.getId(), instance.getInstanceNo());
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        log.info("[请假] 审批通过: instanceId={}, no={}", instance.getId(), instance.getInstanceNo());
    }

    @Override
    public void onRejected(ApprovalInstance instance) {
        log.info("[请假] 审批驳回: instanceId={}, no={}", instance.getId(), instance.getInstanceNo());
    }

    @Override
    public void onCanceled(ApprovalInstance instance) {
        log.info("[请假] 已撤回: instanceId={}, no={}", instance.getId(), instance.getInstanceNo());
    }

    private LeaveRequest findByInstance(ApprovalInstance instance) {
        if (instance.getBizId() != null && instance.getBizId() > 0) {
            LeaveRequest byId = leaveRequestMapper.selectById(instance.getBizId());
            if (byId != null) {
                return byId;
            }
        }
        return leaveRequestMapper.selectOne(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApprovalInstanceId, instance.getId())
                .last("LIMIT 1"));
    }

    private LeaveRequest findOverlap(Long applicantId, LocalDateTime start, LocalDateTime end) {
        List<LeaveRequest> existing = leaveRequestMapper.selectList(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApplicantId, applicantId)
                .in(LeaveRequest::getApprovalStatus,
                        ApprovalInstanceStatus.RUNNING.name(), ApprovalInstanceStatus.APPROVED.name()));
        for (LeaveRequest request : existing) {
            if (request.getStartTime() == null || request.getEndTime() == null) {
                continue;
            }
            if (start.isBefore(request.getEndTime()) && end.isAfter(request.getStartTime())) {
                return request;
            }
        }
        return null;
    }

    private LeaveType requireType(ApprovalForm form) {
        LeaveType type = LeaveType.from(form.getString("leaveType"));
        if (type == null) {
            throw new BusinessException(400, "请假类型不合法");
        }
        return type;
    }

    private LocalDateTime requireTime(String value, String label) {
        LocalDateTime time = parseTime(value);
        if (time == null) {
            throw new BusinessException(400, label + "格式不正确，应为 yyyy-MM-dd HH:mm");
        }
        return time;
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private List<String> parseAttachments(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && StrUtil.isNotBlank(String.valueOf(item)))
                    .map(String::valueOf)
                    .toList();
        }
        if (value instanceof String text) {
            if (StrUtil.isBlank(text)) {
                return List.of();
            }
            try {
                List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
                return parsed == null ? List.of() : parsed;
            } catch (Exception e) {
                return List.of(text);
            }
        }
        return List.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "请假附件序列化失败");
        }
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
