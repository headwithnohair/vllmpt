package org.albedo.vllmpt.module.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.engine.condition.JsonConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConditionEvaluatorTest {

    private JsonConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JsonConditionEvaluator(new ObjectMapper());
    }

    @Test
    void blankConditionAlwaysMatches() {
        assertTrue(evaluator.matches(null, Map.of()));
        assertTrue(evaluator.matches("", Map.of()));
    }

    @Test
    void numericComparison() {
        Map<String, Object> facts = Map.of("amount", 8000);
        assertTrue(evaluator.matches("{\"field\":\"amount\",\"op\":\"GT\",\"value\":5000}", facts));
        assertFalse(evaluator.matches("{\"field\":\"amount\",\"op\":\"LT\",\"value\":5000}", facts));
        assertTrue(evaluator.matches("{\"field\":\"amount\",\"op\":\"GTE\",\"value\":8000}", facts));
    }

    @Test
    void andOrNot() {
        Map<String, Object> facts = Map.of("days", 2, "leaveType", "ANNUAL");

        assertTrue(evaluator.matches("""
                {"op":"AND","children":[
                    {"field":"days","op":"LTE","value":3},
                    {"field":"leaveType","op":"EQ","value":"ANNUAL"}
                ]}
                """, facts));

        assertTrue(evaluator.matches("""
                {"op":"OR","children":[
                    {"field":"days","op":"GT","value":10},
                    {"field":"leaveType","op":"EQ","value":"ANNUAL"}
                ]}
                """, facts));

        assertTrue(evaluator.matches("""
                {"op":"NOT","child":{"field":"days","op":"GT","value":10}}
                """, facts));
    }

    @Test
    void inAndContains() {
        Map<String, Object> facts = Map.of(
                "city", "上海",
                "tags", List.of("紧急", "外包"));

        assertTrue(evaluator.matches("{\"field\":\"city\",\"op\":\"IN\",\"value\":[\"上海\",\"北京\"]}", facts));
        assertFalse(evaluator.matches("{\"field\":\"city\",\"op\":\"NOT_IN\",\"value\":[\"上海\",\"北京\"]}", facts));
        assertTrue(evaluator.matches("{\"field\":\"tags\",\"op\":\"CONTAINS\",\"value\":\"紧急\"}", facts));
    }

    @Test
    void missingFieldSemantics() {
        Map<String, Object> facts = Map.of();

        assertFalse(evaluator.matches("{\"field\":\"amount\",\"op\":\"GT\",\"value\":5000}", facts));
        assertTrue(evaluator.matches("{\"field\":\"amount\",\"op\":\"NOT_IN\",\"value\":[1,2]}", facts));
        assertFalse(evaluator.matches("{\"field\":\"amount\",\"op\":\"EXISTS\"}", facts));
    }

    @Test
    void invalidOperatorThrows() {
        assertThrows(BusinessException.class, () ->
                evaluator.matches("{\"field\":\"amount\",\"op\":\"BETWEEN\",\"value\":1}",
                        Map.of("amount", 1)));
    }
}
