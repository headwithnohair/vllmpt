package org.albedo.vllmpt.module.approval.biz.leave.domain.model;

import org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType;

import java.math.BigDecimal;

/**
 * 请假自动审批策略（P3 默认值；后续可迁移到 leave_policy 表做后台配置）。
 *
 * @param type                请假类型
 * @param autoApproveEnabled  是否允许 AI 自动通过
 * @param autoApproveMaxDays  自动通过的最大天数
 * @param maxConsecutiveDays  单次最大连续天数
 * @param noticeDays          最少提前申请天数
 * @param requireAttachment   是否要求证明材料（如病假）
 * @param allowProbation      试用期是否允许自动通过
 * @param maxRequests30d      30 天内最大请假次数
 * @param minConfidence       最低置信度
 * @param version             策略版本
 */
public record LeavePolicy(LeaveType type,
                          boolean autoApproveEnabled,
                          BigDecimal autoApproveMaxDays,
                          BigDecimal maxConsecutiveDays,
                          int noticeDays,
                          boolean requireAttachment,
                          boolean allowProbation,
                          int maxRequests30d,
                          double minConfidence,
                          String version) {

    public static LeavePolicy defaults(LeaveType type) {
        if (type == null) {
            throw new IllegalArgumentException("请假类型不能为空");
        }
        return switch (type) {
            case ANNUAL -> new LeavePolicy(type, true,
                    new BigDecimal("2.0"), new BigDecimal("5.0"), 1,
                    false, false, 3, 0.80, "v1");
            case SICK -> new LeavePolicy(type, true,
                    new BigDecimal("1.0"), new BigDecimal("3.0"), 0,
                    true, false, 3, 0.85, "v1");
            case PERSONAL -> new LeavePolicy(type, false,
                    BigDecimal.ZERO, new BigDecimal("3.0"), 1,
                    false, false, 2, 0.90, "v1");
            case COMP_TIME -> new LeavePolicy(type, true,
                    new BigDecimal("1.0"), new BigDecimal("3.0"), 1,
                    false, false, 3, 0.85, "v1");
        };
    }
}
