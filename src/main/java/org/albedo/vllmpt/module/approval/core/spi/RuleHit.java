package org.albedo.vllmpt.module.approval.core.spi;

/**
 * 命中的硬规则。
 *
 * @param code    规则编码，用于审计与复盘
 * @param message 规则说明
 * @param effect  裁决效果
 */
public record RuleHit(String code, String message, RuleEffect effect) {

    public boolean reject() {
        return effect == RuleEffect.REJECT;
    }

    public boolean escalate() {
        return effect == RuleEffect.ESCALATE;
    }

    public static RuleHit reject(String code, String message) {
        return new RuleHit(code, message, RuleEffect.REJECT);
    }

    public static RuleHit escalate(String code, String message) {
        return new RuleHit(code, message, RuleEffect.ESCALATE);
    }

    public static RuleHit pass(String code, String message) {
        return new RuleHit(code, message, RuleEffect.PASS);
    }
}
