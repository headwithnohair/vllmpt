package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 表单 Schema 校验。
 * <p>
 * Schema 结构：{"fields":[{"name":"days","label":"请假天数","type":"NUMBER","required":true}]}。
 * 这里只做通用必填/类型校验，业务规则由 {@code ApprovalBizHandler.validate} 负责（P1）。
 */
@Component
@RequiredArgsConstructor
public class FormSchemaValidator {

    private final ObjectMapper objectMapper;

    public void validate(String schemaJson, Map<String, Object> form) {
        if (StrUtil.isBlank(schemaJson)) {
            return;
        }
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "表单 Schema 解析失败");
        }
        JsonNode fields = schema.path("fields");
        if (!fields.isArray()) {
            return;
        }

        List<String> errors = new ArrayList<>();
        for (JsonNode field : fields) {
            String name = field.path("name").asText("");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            String label = field.path("label").asText(name);
            Object value = form.get(name);
            if (isEmpty(value)) {
                if (field.path("required").asBoolean(false)) {
                    errors.add(label + "不能为空");
                }
                continue;
            }
            String type = field.path("type").asText("STRING").trim().toUpperCase();
            if (!matchesType(type, value)) {
                errors.add(label + "类型不正确");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(400, String.join("；", errors));
        }
    }

    /**
     * 计算缺失的必填字段名（供草稿/AI 填表使用，不抛异常）。
     */
    public List<String> missingFields(String schemaJson, Map<String, Object> form) {
        if (StrUtil.isBlank(schemaJson)) {
            return List.of();
        }
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "表单 Schema 解析失败");
        }
        JsonNode fields = schema.path("fields");
        if (!fields.isArray()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (JsonNode field : fields) {
            String name = field.path("name").asText("");
            if (StrUtil.isBlank(name) || !field.path("required").asBoolean(false)) {
                continue;
            }
            if (isEmpty(form.get(name))) {
                missing.add(name);
            }
        }
        return missing;
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return StrUtil.isBlank(text);
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private boolean matchesType(String type, Object value) {
        return switch (type) {
            case "NUMBER" -> NumberUtil.isNumber(String.valueOf(value));
            case "BOOLEAN" -> value instanceof Boolean
                    || "true".equalsIgnoreCase(String.valueOf(value))
                    || "false".equalsIgnoreCase(String.valueOf(value));
            case "ARRAY" -> value instanceof Collection<?> || value.getClass().isArray();
            case "OBJECT" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }
}
