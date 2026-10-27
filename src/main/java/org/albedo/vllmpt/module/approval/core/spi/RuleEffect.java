package org.albedo.vllmpt.module.approval.core.spi;

/**
 * 单条规则的裁决效果。
 */
public enum RuleEffect {
    PASS,
    REJECT,
    ESCALATE
}
