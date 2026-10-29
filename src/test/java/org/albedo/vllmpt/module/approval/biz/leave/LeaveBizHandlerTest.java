package org.albedo.vllmpt.module.approval.biz.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveRequest;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.LeaveRequestMapper;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveBalanceService;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveBizHandler;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveCalendarService;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveBizHandlerTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LeaveRequestMapper leaveRequestMapper;
    private LeaveCalendarService calendarService;
    private LeaveBalanceService balanceService;
    private LeaveBizHandler handler;

    @BeforeAll
    static void initTableInfo() {
        // 单元测试脱离 Spring 时，MyBatis-Plus 的 LambdaUpdateWrapper 需要手动初始化实体元数据
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, LeaveRequest.class);
    }

    @BeforeEach
    void setUp() {
        leaveRequestMapper = mock(LeaveRequestMapper.class);
        calendarService = mock(LeaveCalendarService.class);
        balanceService = mock(LeaveBalanceService.class);
        handler = new LeaveBizHandler(leaveRequestMapper, calendarService, balanceService, new ObjectMapper());
        when(leaveRequestMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void rejectsInvalidTimeRange() {
        Map<String, Object> form = form("ANNUAL", at(3, 9), at(2, 18), "回老家");

        ValidationResult result = handler.validate(new ApprovalForm("LEAVE", form), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("结束时间")));
    }

    @Test
    void rejectsAnnualWithoutEnoughBalance() {
        mockDays("2.0");
        when(balanceService.available(any(), any(), anyInt())).thenReturn(new BigDecimal("1.0"));

        ValidationResult result = handler.validate(
                new ApprovalForm("LEAVE", form("ANNUAL", at(2, 9), at(3, 18), "回老家")), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("余额不足")));
    }

    @Test
    void rejectsSickLeaveWithoutAttachment() {
        mockDays("2.0");

        ValidationResult result = handler.validate(
                new ApprovalForm("LEAVE", form("SICK", at(2, 9), at(3, 18), "感冒")), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("证明材料")));
    }

    @Test
    void rejectsOverlappingLeave() {
        mockDays("1.0");
        LeaveRequest existing = new LeaveRequest();
        existing.setStartTime(dateTime(2, 9));
        existing.setEndTime(dateTime(2, 18));
        when(leaveRequestMapper.selectList(any())).thenReturn(List.of(existing));

        ValidationResult result = handler.validate(
                new ApprovalForm("LEAVE", form("PERSONAL", at(2, 14), at(2, 18), "办事")), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("重叠")));
    }

    @Test
    void acceptsValidLeave() {
        mockDays("2.0");
        when(balanceService.available(any(), any(), anyInt())).thenReturn(new BigDecimal("5.0"));

        ValidationResult result = handler.validate(
                new ApprovalForm("LEAVE", form("ANNUAL", at(2, 9), at(3, 18), "回老家")), ctx());

        assertTrue(result.valid(), () -> String.join("；", result.errors()));
    }

    @Test
    void createBizRecordLocksBalanceAndInserts() {
        mockDays("2.0");
        when(leaveRequestMapper.insert(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest request = invocation.getArgument(0);
            request.setId(99L);
            return 1;
        });

        Long bizId = handler.createBizRecord(
                new ApprovalForm("LEAVE", form("ANNUAL", at(2, 9), at(3, 18), "回老家")), ctx());

        assertEquals(99L, bizId);
        verify(balanceService).lock(1L, org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType.ANNUAL,
                new BigDecimal("2.0"), LocalDate.now().getYear() + 1);
    }

    @Test
    void approvedResultDeductsBalance() {
        LeaveRequest request = new LeaveRequest();
        request.setId(9L);
        request.setApplicantId(1L);
        request.setLeaveType("ANNUAL");
        request.setLeaveDays(new BigDecimal("2.0"));
        request.setStartTime(dateTime(2, 9));
        request.setApprovalStatus("RUNNING");
        when(leaveRequestMapper.selectById(9L)).thenReturn(request);
        when(balanceService.approve(any(), any(), any(), anyInt())).thenReturn(true);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(100L);
        instance.setBizId(9L);

        handler.applyResult(instance, ApprovalInstanceStatus.APPROVED);

        verify(balanceService).approve(1L,
                org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType.ANNUAL,
                new BigDecimal("2.0"), LocalDate.now().getYear() + 1);
        verify(leaveRequestMapper).update(any(), any());
    }

    @Test
    void extractFactsContainsComputedDays() {
        mockDays("2.0");
        when(balanceService.available(any(), any(), anyInt())).thenReturn(new BigDecimal("5.0"));

        Map<String, Object> facts = handler.extractFacts(
                new ApprovalForm("LEAVE", form("ANNUAL", at(2, 9), at(3, 18), "回老家")), ctx());

        assertEquals("ANNUAL", facts.get("leaveType"));
        assertEquals(0, ((BigDecimal) facts.get("leaveDays")).compareTo(new BigDecimal("2.0")));
        assertEquals(false, facts.get("hasAttachment"));
    }

    private String at(int plusDays, int hour) {
        return dateTime(plusDays, hour).format(TIME_FORMAT);
    }

    private LocalDateTime dateTime(int plusDays, int hour) {
        // 用“明年 6 月 1 日”作为基准，避免测试随当前日期漂移或跨年
        return LocalDate.of(LocalDate.now().getYear() + 1, 6, 1)
                .plusDays(plusDays).atTime(hour, 0);
    }

    private Map<String, Object> form(String type, String start, String end, String reason) {
        return new java.util.LinkedHashMap<>(Map.of(
                "leaveType", type,
                "startTime", start,
                "endTime", end,
                "reason", reason));
    }

    private ApprovalContext ctx() {
        return new ApprovalContext(1L, "LEAVE", null, Map.of());
    }

    private void mockDays(String days) {
        when(calendarService.calculateDays(any(), any())).thenReturn(new BigDecimal(days));
    }
}
