package org.albedo.vllmpt.module.approval.biz.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.module.approval.biz.travel.domain.entity.TravelExpense;
import org.albedo.vllmpt.module.approval.biz.travel.mapper.TravelExpenseMapper;
import org.albedo.vllmpt.module.approval.biz.travel.service.TravelBizHandler;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelBizHandlerTest {

    private TravelExpenseMapper mapper;
    private TravelBizHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(TravelExpenseMapper.class);
        handler = new TravelBizHandler(mapper, new ObjectMapper());
    }

    @Test
    void acceptsValidTravel() {
        ValidationResult result = handler.validate(new ApprovalForm("TRAVEL_EXPENSE", form("3000")), ctx());
        assertTrue(result.valid(), () -> String.join("；", result.errors()));
    }

    @Test
    void rejectsInvalidDateAndAmount() {
        Map<String, Object> form = form("0");
        form.put("startDate", LocalDate.now().plusDays(3).toString());
        form.put("endDate", LocalDate.now().plusDays(2).toString());

        ValidationResult result = handler.validate(new ApprovalForm("TRAVEL_EXPENSE", form), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("结束日期")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("预计金额")));
    }

    @Test
    void createRecordSetsFactsAlias() {
        when(mapper.insert(any(TravelExpense.class))).thenAnswer(invocation -> {
            TravelExpense expense = invocation.getArgument(0);
            expense.setId(9L);
            return 1;
        });

        Long id = handler.createBizRecord(new ApprovalForm("TRAVEL_EXPENSE", form("3000")), ctx());
        assertEquals(9L, id);

        Map<String, Object> facts = handler.extractFacts(
                new ApprovalForm("TRAVEL_EXPENSE", form("3000")), ctx());
        assertEquals(0, ((BigDecimal) facts.get("amount")).compareTo(new BigDecimal("3000")));
        assertEquals(2L, facts.get("days"));
        verify(mapper).insert(any(TravelExpense.class));
    }

    private Map<String, Object> form(String amount) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("destination", "上海");
        form.put("startDate", LocalDate.now().plusDays(2).toString());
        form.put("endDate", LocalDate.now().plusDays(3).toString());
        form.put("estimatedAmount", amount);
        form.put("currency", "CNY");
        form.put("costCenter", "RD");
        form.put("reason", "客户方案评审");
        form.put("invoiceUrls", java.util.List.of());
        return form;
    }

    private ApprovalContext ctx() {
        return new ApprovalContext(1L, "TRAVEL_EXPENSE", null, Map.of());
    }
}
