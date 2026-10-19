package org.albedo.vllmpt.module.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.service.FormSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormSchemaValidatorTest {

    private static final String SCHEMA = """
            {"fields":[
                {"name":"leaveType","label":"请假类型","type":"STRING","required":true},
                {"name":"days","label":"请假天数","type":"NUMBER","required":true},
                {"name":"urgent","label":"是否紧急","type":"BOOLEAN","required":false}
            ]}
            """;

    private FormSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FormSchemaValidator(new ObjectMapper());
    }

    @Test
    void validFormPasses() {
        assertDoesNotThrow(() -> validator.validate(SCHEMA,
                Map.of("leaveType", "ANNUAL", "days", 2, "urgent", true)));
    }

    @Test
    void requiredFieldMissing() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(SCHEMA, Map.of("days", 2)));
        assertTrue(ex.getMessage().contains("请假类型不能为空"));
    }

    @Test
    void typeMismatch() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(SCHEMA,
                        Map.of("leaveType", "ANNUAL", "days", "两天")));
        assertTrue(ex.getMessage().contains("请假天数类型不正确"));
    }

    @Test
    void blankSchemaSkipsValidation() {
        assertDoesNotThrow(() -> validator.validate(null, Map.of()));
    }
}
