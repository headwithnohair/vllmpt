package org.albedo.vllmpt.module.approval.core.engine.assignee;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 审批人配置解析工具。
 */
final class AssigneeConfig {

    private AssigneeConfig() {
    }

    static JsonNode parse(ObjectMapper objectMapper, AssigneeSpec spec) {
        if (StrUtil.isBlank(spec.configJson())) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(spec.configJson());
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "审批人配置解析失败: " + spec.type());
        }
    }

    /**
     * 读取 {"userIds":[...]} 或 {"userId":1} 形式的用户 ID。
     */
    static List<Long> readIds(JsonNode config, String arrayField, String singleField) {
        Set<Long> ids = new LinkedHashSet<>();
        JsonNode array = config.get(arrayField);
        if (array != null && array.isArray()) {
            array.forEach(item -> addId(ids, item));
        }
        JsonNode single = config.get(singleField);
        if (single != null && !single.isNull()) {
            addId(ids, single);
        }
        return new ArrayList<>(ids);
    }

    /**
     * 把表单字段值转换成用户 ID 列表，支持数字/字符串/数组/集合。
     */
    static List<Long> toIds(Object value) {
        Set<Long> ids = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addId(ids, item));
        } else {
            addId(ids, value);
        }
        return new ArrayList<>(ids);
    }

    private static void addId(Set<Long> ids, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            ids.add(number.longValue());
            return;
        }
        String text = String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return;
        }
        try {
            ids.add(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            // 非用户 ID 的脏数据直接忽略，由调用方判断是否为空
        }
    }

    private static void addId(Set<Long> ids, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isNumber()) {
            ids.add(node.asLong());
        } else if (node.isTextual()) {
            addId(ids, node.asText());
        }
    }
}
