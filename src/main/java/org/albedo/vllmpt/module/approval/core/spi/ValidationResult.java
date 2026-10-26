package org.albedo.vllmpt.module.approval.core.spi;

import java.util.List;

/**
 * 业务校验结果。
 *
 * @param valid         是否通过
 * @param errors        业务错误，非空即视为失败
 * @param missingFields 缺失字段（AI 填表/草稿使用）
 */
public record ValidationResult(boolean valid, List<String> errors, List<String> missingFields) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult ok(List<String> missingFields) {
        return new ValidationResult(true, List.of(), missingFields);
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }
}
