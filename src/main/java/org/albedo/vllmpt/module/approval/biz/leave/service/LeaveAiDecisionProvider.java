package org.albedo.vllmpt.module.approval.biz.leave.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType;
import org.albedo.vllmpt.module.approval.biz.leave.domain.model.LeavePolicy;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.LeaveRequestMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.spi.AbstractAiDecisionProvider;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionContext;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionResult;
import org.albedo.vllmpt.module.approval.core.spi.LlmAssessment;
import org.albedo.vllmpt.module.approval.core.spi.RuleHit;
import org.albedo.vllmpt.module.user.mapper.SysUserProfileMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUserProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假 AI 决策策略（policyKey=leavePolicy）。
 * <p>
 * 硬规则覆盖时间、余额、冲突、连续天数、提前期、证明材料、试用期、近期频率；
 * 全部通过后才把灰区交给模型做风险评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveAiDecisionProvider extends AbstractAiDecisionProvider {

    public static final String POLICY_KEY = "leavePolicy";

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveBalanceService balanceService;
    private final SysUserProfileMapper userProfileMapper;
    private final LeaveRiskAssessor riskAssessor;

    @Override
    public String policyKey() {
        return POLICY_KEY;
    }

    @Override
    public String policyVersion() {
        return "v1";
    }

    /**
     * 节点/全局最低置信度与业务策略最低置信度取较高值。
     */
    @Override
    public AiDecisionResult decide(AiDecisionContext ctx) {
        LeaveRequest request = findRequest(ctx.instance());
        LeavePolicy policy = request == null ? null : policyOf(request);
        double minConfidence = policy == null
                ? ctx.minConfidence()
                : Math.max(ctx.minConfidence(), policy.minConfidence());
        return super.decide(new AiDecisionContext(ctx.instance(), ctx.node(), ctx.facts(), minConfidence));
    }

    @Override
    protected List<RuleHit> hardRules(AiDecisionContext ctx) {
        List<RuleHit> hits = new ArrayList<>();
        LeaveRequest request = findRequest(ctx.instance());
        if (request == null) {
            hits.add(RuleHit.escalate("LEAVE_NOT_FOUND", "未找到请假业务数据，转人工核实"));
            return hits;
        }
        LeaveType type = LeaveType.from(request.getLeaveType());
        if (type == null) {
            hits.add(RuleHit.escalate("LEAVE_TYPE_INVALID", "请假类型无法识别，转人工核实"));
            return hits;
        }
        LeavePolicy policy = LeavePolicy.defaults(type);

        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (start == null || end == null || !end.isAfter(start)) {
            hits.add(RuleHit.reject("LEAVE_TIME_INVALID", "请假时间不合法"));
            return hits;
        }
        if (start.toLocalDate().isBefore(LocalDate.now())) {
            hits.add(RuleHit.reject("LEAVE_START_IN_PAST", "开始时间已过去"));
        }

        BigDecimal days = request.getLeaveDays() == null ? BigDecimal.ZERO : request.getLeaveDays();
        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            hits.add(RuleHit.reject("LEAVE_DAYS_INVALID", "请假天数必须大于 0"));
        }
        if (days.compareTo(policy.maxConsecutiveDays()) > 0) {
            hits.add(RuleHit.reject("LEAVE_MAX_CONSECUTIVE",
                    "超过单次最大连续天数 " + policy.maxConsecutiveDays().stripTrailingZeros().toPlainString() + " 天"));
        }

        if (type.balanceRequired()) {
            BigDecimal available = balanceService.totalAvailable(
                    request.getApplicantId(), type, start.getYear());
            if (available.compareTo(days) < 0) {
                hits.add(RuleHit.reject("LEAVE_BALANCE_INSUFFICIENT",
                        type.label() + "余额不足，需要复核"));
            }
        }

        LeaveRequest overlap = findOverlap(request);
        if (overlap != null) {
            hits.add(RuleHit.reject("LEAVE_TIME_OVERLAP",
                    "与已有请假时间重叠：" + overlap.getStartTime() + " ~ " + overlap.getEndTime()));
        }

        if (!policy.autoApproveEnabled()) {
            hits.add(RuleHit.escalate("LEAVE_AUTO_APPROVE_DISABLED",
                    type.label() + "不启用自动审批"));
        }
        if (days.compareTo(policy.autoApproveMaxDays()) > 0) {
            hits.add(RuleHit.escalate("LEAVE_EXCEEDS_AUTO_LIMIT",
                    "超过自动审批上限 " + policy.autoApproveMaxDays().stripTrailingZeros().toPlainString() + " 天"));
        }

        long noticeDays = ChronoUnit.DAYS.between(LocalDate.now(), start.toLocalDate());
        if (noticeDays < policy.noticeDays()) {
            hits.add(RuleHit.escalate("LEAVE_NOTICE_INSUFFICIENT",
                    "未提前 " + policy.noticeDays() + " 天申请"));
        }

        if (policy.requireAttachment() && days.compareTo(BigDecimal.ONE) > 0
                && !hasAttachment(request)) {
            hits.add(RuleHit.escalate("LEAVE_ATTACHMENT_MISSING",
                    type.label() + "超过 1 天需上传证明材料"));
        }

        SysUserProfile profile = userProfileMapper.selectById(request.getApplicantId());
        if (profile == null) {
            hits.add(RuleHit.escalate("LEAVE_PROFILE_MISSING", "员工档案缺失，无法判断试用期/在职状态"));
        } else {
            if ("PROBATION".equalsIgnoreCase(profile.getEmploymentStatus()) && !policy.allowProbation()) {
                hits.add(RuleHit.escalate("LEAVE_PROBATION", "试用期员工请假需人工审批"));
            }
            if ("LEFT".equalsIgnoreCase(profile.getEmploymentStatus())) {
                hits.add(RuleHit.escalate("LEAVE_EMPLOYEE_LEFT", "员工已离职，需人工核实"));
            }
        }

        long recentCount = countRecent(request);
        if (recentCount >= policy.maxRequests30d()) {
            hits.add(RuleHit.escalate("LEAVE_FREQUENT",
                    "近 30 天请假 " + recentCount + " 次，超过阈值 " + policy.maxRequests30d()));
        }

        if (looksLikePromptInjection(request.getReason())) {
            hits.add(RuleHit.escalate("LEAVE_PROMPT_INJECTION",
                    "请假理由包含疑似指令，转人工核实"));
        }
        return hits;
    }

    @Override
    protected LlmAssessment assess(AiDecisionContext ctx, List<RuleHit> rules) {
        LeaveRequest request = findRequest(ctx.instance());
        if (request == null) {
            return LlmAssessment.failed("未找到请假业务数据", null);
        }
        LeaveType type = LeaveType.from(request.getLeaveType());
        if (type == null) {
            return LlmAssessment.failed("请假类型无法识别", null);
        }
        LeavePolicy policy = LeavePolicy.defaults(type);
        SysUserProfile profile = userProfileMapper.selectById(request.getApplicantId());

        Map<String, Object> facts = new LinkedHashMap<>(ctx.facts());
        facts.put("leaveType", type.name());
        facts.put("leaveTypeLabel", type.label());
        facts.put("leaveDays", request.getLeaveDays());
        facts.put("startTime", request.getStartTime());
        facts.put("endTime", request.getEndTime());
        facts.put("reason", request.getReason());
        facts.put("hasAttachment", hasAttachment(request));
        facts.put("employmentStatus", profile == null ? "UNKNOWN" : profile.getEmploymentStatus());
        facts.put("recentLeaveCount30d", countRecent(request));
        if (type.balanceRequired()) {
            facts.put("balanceAvailable", balanceService.totalAvailable(
                    request.getApplicantId(), type, request.getStartTime().getYear()));
        }
        return riskAssessor.assess(facts, policy, rules);
    }

    private LeaveRequest findRequest(ApprovalInstance instance) {
        if (instance.getBizId() != null && instance.getBizId() > 0) {
            LeaveRequest request = leaveRequestMapper.selectById(instance.getBizId());
            if (request != null) {
                return request;
            }
        }
        return leaveRequestMapper.selectOne(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApprovalInstanceId, instance.getId())
                .last("LIMIT 1"));
    }

    private LeavePolicy policyOf(LeaveRequest request) {
        LeaveType type = LeaveType.from(request.getLeaveType());
        return type == null ? null : LeavePolicy.defaults(type);
    }

    private LeaveRequest findOverlap(LeaveRequest current) {
        List<LeaveRequest> others = leaveRequestMapper.selectList(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApplicantId, current.getApplicantId())
                .ne(LeaveRequest::getId, current.getId())
                .in(LeaveRequest::getApprovalStatus, "RUNNING", "APPROVED"));
        for (LeaveRequest other : others) {
            if (other.getStartTime() == null || other.getEndTime() == null) {
                continue;
            }
            if (current.getStartTime().isBefore(other.getEndTime())
                    && current.getEndTime().isAfter(other.getStartTime())) {
                return other;
            }
        }
        return null;
    }

    private long countRecent(LeaveRequest current) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        Long count = leaveRequestMapper.selectCount(Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApplicantId, current.getApplicantId())
                .ne(LeaveRequest::getId, current.getId())
                .ge(LeaveRequest::getCreatedAt, from)
                .in(LeaveRequest::getApprovalStatus, "RUNNING", "APPROVED", "REJECTED"));
        return count == null ? 0 : count;
    }

    private boolean hasAttachment(LeaveRequest request) {
        String urls = request.getAttachmentUrls();
        return StrUtil.isNotBlank(urls) && !"[]".equals(urls.trim());
    }

    private boolean looksLikePromptInjection(String reason) {
        if (StrUtil.isBlank(reason)) {
            return false;
        }
        return reason.contains("忽略规则") || reason.contains("忽略以上")
                || reason.contains("直接批准") || reason.contains("跳过审批");
    }
}
