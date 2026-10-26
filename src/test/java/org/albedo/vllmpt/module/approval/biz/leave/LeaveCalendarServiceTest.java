package org.albedo.vllmpt.module.approval.biz.leave;

import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.SysHoliday;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.SysHolidayMapper;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaveCalendarServiceTest {

    private SysHolidayMapper holidayMapper;
    private LeaveCalendarService service;

    @BeforeEach
    void setUp() {
        holidayMapper = mock(SysHolidayMapper.class);
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        service = new LeaveCalendarService(holidayMapper);
    }

    @Test
    void countsWeekdays() {
        // 2026-10-26（周一）~ 2026-10-30（周五）
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 26, 9, 0),
                LocalDateTime.of(2026, 10, 30, 18, 0));
        assertEquals(0, days.compareTo(new BigDecimal("5.0")));
    }

    @Test
    void skipsWeekend() {
        // 2026-10-31（周六）~ 2026-11-01（周日）
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 31, 9, 0),
                LocalDateTime.of(2026, 11, 1, 18, 0));
        assertEquals(0, days.compareTo(BigDecimal.ZERO));
    }

    @Test
    void countsAfternoonAsHalfDay() {
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 26, 14, 0),
                LocalDateTime.of(2026, 10, 26, 18, 0));
        assertEquals(0, days.compareTo(new BigDecimal("0.5")));
    }

    @Test
    void countsMorningAsHalfDay() {
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 26, 9, 0),
                LocalDateTime.of(2026, 10, 26, 12, 0));
        assertEquals(0, days.compareTo(new BigDecimal("0.5")));
    }

    @Test
    void countsCrossDayHalfDays() {
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 26, 14, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0));
        assertEquals(0, days.compareTo(BigDecimal.ONE));
    }

    @Test
    void excludesHoliday() {
        SysHoliday holiday = new SysHoliday();
        holiday.setHolidayDate(LocalDate.of(2026, 10, 28));
        holiday.setDayType("HOLIDAY");
        when(holidayMapper.selectList(any())).thenReturn(List.of(holiday));

        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 26, 9, 0),
                LocalDateTime.of(2026, 10, 30, 18, 0));
        assertEquals(0, days.compareTo(new BigDecimal("4.0")));
    }

    @Test
    void includesMakeUpWorkday() {
        SysHoliday workday = new SysHoliday();
        workday.setHolidayDate(LocalDate.of(2026, 10, 31));
        workday.setDayType("WORKDAY");
        when(holidayMapper.selectList(any())).thenReturn(List.of(workday));

        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 31, 9, 0),
                LocalDateTime.of(2026, 10, 31, 18, 0));
        assertEquals(0, days.compareTo(BigDecimal.ONE));
    }

    @Test
    void invalidRangeReturnsZero() {
        BigDecimal days = service.calculateDays(
                LocalDateTime.of(2026, 10, 27, 9, 0),
                LocalDateTime.of(2026, 10, 26, 18, 0));
        assertEquals(0, days.compareTo(BigDecimal.ZERO));
    }
}
