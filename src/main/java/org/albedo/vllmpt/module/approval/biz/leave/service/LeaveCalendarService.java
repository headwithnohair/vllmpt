package org.albedo.vllmpt.module.approval.biz.leave.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.SysHoliday;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.SysHolidayMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假日历：计算工作日天数，支持半天与节假日/调休。
 * <p>
 * 规则：
 * <ul>
 *   <li>工作日 = 周一至周五，排除 {@code sys_holiday.day_type=HOLIDAY}；</li>
 *   <li>{@code WORKDAY} 的周末算工作日（调休上班）；</li>
 *   <li>开始时间 ≥ 13:00 记 0.5 天；结束时间 ≤ 12:00 记 0.5 天；</li>
 *   <li>表为空时完全按周一至周五计算。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LeaveCalendarService {

    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime NOON = LocalTime.NOON;

    private final SysHolidayMapper holidayMapper;

    /**
     * 计算请假天数，非法区间返回 0。
     */
    public BigDecimal calculateDays(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO;
        }
        LocalDate first = start.toLocalDate();
        LocalDate last = end.toLocalDate();
        Map<LocalDate, String> overrides = loadOverrides(first, last);

        BigDecimal days = BigDecimal.ZERO;
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!isWorkday(date, overrides)) {
                continue;
            }
            boolean startHalf = date.equals(first) && !start.toLocalTime().isBefore(AFTERNOON_START);
            boolean endHalf = date.equals(last) && !end.toLocalTime().isAfter(NOON);
            if (startHalf || endHalf) {
                days = days.add(new BigDecimal("0.5"));
            } else {
                days = days.add(BigDecimal.ONE);
            }
        }
        return days;
    }

    public boolean isWorkday(LocalDate date) {
        return isWorkday(date, loadOverrides(date, date));
    }

    private boolean isWorkday(LocalDate date, Map<LocalDate, String> overrides) {
        String override = overrides.get(date);
        if ("HOLIDAY".equalsIgnoreCase(override)) {
            return false;
        }
        if ("WORKDAY".equalsIgnoreCase(override)) {
            return true;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private Map<LocalDate, String> loadOverrides(LocalDate first, LocalDate last) {
        List<SysHoliday> holidays = holidayMapper.selectList(Wrappers.<SysHoliday>lambdaQuery()
                .between(SysHoliday::getHolidayDate, first, last));
        if (holidays == null || holidays.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, String> result = new HashMap<>();
        for (SysHoliday holiday : holidays) {
            if (holiday.getHolidayDate() != null && holiday.getDayType() != null) {
                result.put(holiday.getHolidayDate(), holiday.getDayType());
            }
        }
        return result;
    }
}
