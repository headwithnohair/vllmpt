package org.albedo.vllmpt.module.approval.biz.leave.domain.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 请假类型。
 */
public enum LeaveType {

    ANNUAL("年假", true),
    SICK("病假", false),
    PERSONAL("事假", false),
    COMP_TIME("调休", true);

    private final String label;
    private final boolean balanceRequired;

    LeaveType(String label, boolean balanceRequired) {
        this.label = label;
        this.balanceRequired = balanceRequired;
    }

    public String label() {
        return label;
    }

    /**
     * 是否需要占用假期余额（年假、调休）。
     */
    public boolean balanceRequired() {
        return balanceRequired;
    }

    /**
     * @return 无法识别时返回 null
     */
    public static LeaveType from(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
