package org.albedo.vllmpt.module.approval.core.engine.condition;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON 条件求值器。
 * <p>
 * 不使用 SpEL，避免表达式注入。支持的条件 DSL：
 * <pre>
 * {"field":"days","op":"LTE","value":3}
 * {"op":"AND","children":[
 *     {"field":"amount","op":"GT","value":5000},
 *     {"field":"city","op":"IN","value":["上海","北京"]}
 * ]}
 * </pre>
 * 支持的运算符：AND / OR / NOT / EQ / NE / GT / GTE / LT / LTE / IN / NOT_IN / CONTAINS / EXISTS。
 */
@Component
@RequiredArgsConstructor
public class JsonConditionEvaluator {

    private final ObjectMapper objectMapper;

    public boolean matches(String conditionJson, Map<String, Object> facts) {
        if (StrUtil.isBlank(conditionJson)) {
            return true;
        }
        try {
            return evaluate(objectMapper.readTree(conditionJson), facts);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "审批条件 JSON 解析失败: " + e.getOriginalMessage());
        }
    }

    private boolean evaluate(JsonNode node, Map<String, Object> facts) {
        if (node == null || node.isNull() || (node.isObject() && node.isEmpty())) {
            return true;
        }
        String op = node.path("op").asText("").trim().toUpperCase();
        if (op.isEmpty() && node.has("children")) {
            op = "AND";
        }
        switch (op) {
            case "AND" -> {
                for (JsonNode child : children(node)) {
                    if (!evaluate(child, facts)) {
                        return false;
                    }
                }
                return true;
            }
            case "OR" -> {
                for (JsonNode child : children(node)) {
                    if (evaluate(child, facts)) {
                        return true;
                    }
                }
                return false;
            }
            case "NOT" -> {
                JsonNode child = node.has("child") ? node.get("child") : node.path("children").path(0);
                return !evaluate(child, facts);
            }
            default -> {
                // 继续按字段条件处理
            }
        }

        String field = node.path("field").asText("").trim();
        if (field.isEmpty()) {
            throw new BusinessException(400, "审批条件缺少 field: " + node);
        }
        Object actual = facts.get(field);
        if ("EXISTS".equals(op)) {
            return actual != null;
        }
        return compare(actual, node.get("value"), op);
    }

    private boolean compare(Object actual, JsonNode expected, String op) {
        return switch (op) {
            case "EQ" -> equalsValue(actual, expected);
            case "NE" -> !equalsValue(actual, expected);
            case "GT", "GTE", "LT", "LTE" -> compareOrder(actual, expected, op);
            case "IN" -> in(actual, expected, true);
            case "NOT_IN" -> in(actual, expected, false);
            case "CONTAINS" -> contains(actual, expected);
            default -> throw new BusinessException(400, "不支持的审批条件运算符: " + op);
        };
    }

    private boolean equalsValue(Object actual, JsonNode expected) {
        if (actual == null || expected == null || expected.isNull()) {
            return false;
        }
        if (expected.isNumber()) {
            BigDecimal left = toDecimal(actual);
            return left != null && left.compareTo(expected.decimalValue()) == 0;
        }
        if (expected.isBoolean()) {
            return String.valueOf(actual).equalsIgnoreCase(expected.asText());
        }
        return String.valueOf(actual).equals(expected.asText());
    }

    private boolean compareOrder(Object actual, JsonNode expected, String op) {
        if (actual == null || expected == null || expected.isNull()) {
            return false;
        }
        int cmp;
        BigDecimal left = toDecimal(actual);
        BigDecimal right = toDecimal(expected.isNumber() ? expected.decimalValue() : expected.asText());
        if (left != null && right != null) {
            cmp = left.compareTo(right);
        } else {
            cmp = String.valueOf(actual).compareTo(expected.asText());
        }
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            default -> false;
        };
    }

    private boolean in(Object actual, JsonNode expected, boolean expectHit) {
        if (actual == null) {
            return !expectHit;
        }
        if (expected == null || expected.isNull()) {
            return false;
        }
        Set<String> expectedValues = new LinkedHashSet<>();
        if (expected.isArray()) {
            expected.forEach(item -> expectedValues.add(item.asText()));
        } else {
            expectedValues.add(expected.asText());
        }

        Collection<?> actualValues = actual instanceof Collection<?> collection ? collection : List.of(actual);
        boolean hit = actualValues.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> expectedValues.contains(String.valueOf(value)));
        return expectHit == hit;
    }

    private boolean contains(Object actual, JsonNode expected) {
        if (actual == null || expected == null || expected.isNull()) {
            return false;
        }
        String target = expected.asText();
        if (actual instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).anyMatch(item -> String.valueOf(item).equals(target));
        }
        if (actual instanceof Map<?, ?> map) {
            return map.containsKey(target);
        }
        return String.valueOf(actual).contains(target);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        if (NumberUtil.isNumber(text)) {
            return new BigDecimal(text);
        }
        return null;
    }

    private List<JsonNode> children(JsonNode node) {
        JsonNode children = node.get("children");
        if (children == null || !children.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>();
        children.forEach(list::add);
        return list;
    }
}
