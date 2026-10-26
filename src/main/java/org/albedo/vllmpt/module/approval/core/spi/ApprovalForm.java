package org.albedo.vllmpt.module.approval.core.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务表单：提交审批时由通用接口传入，核心只当作 JSON 处理。
 *
 * @param bizType 业务类型，如 LEAVE
 * @param values  表单字段
 */
public record ApprovalForm(String bizType, Map<String, Object> values) {

    public ApprovalForm {
        values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
