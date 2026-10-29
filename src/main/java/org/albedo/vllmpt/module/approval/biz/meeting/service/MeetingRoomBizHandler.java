package org.albedo.vllmpt.module.approval.biz.meeting.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoom;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoomBooking;
import org.albedo.vllmpt.module.approval.biz.meeting.mapper.MeetingRoomBookingMapper;
import org.albedo.vllmpt.module.approval.biz.meeting.mapper.MeetingRoomMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalBizHandler;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议室预约业务插件。
 * <p>
 * 校验会议室启用状态、容量、开放时间、时间冲突；创建时对会议室行加锁并复核冲突，
 * 避免并发提交造成同一时段重复预约。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRoomBizHandler implements ApprovalBizHandler {

    public static final String BIZ_TYPE = "MEETING_ROOM";
    public static final String TEMPLATE_KEY = "meeting_room";

    private static final LocalTime OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

    private final MeetingRoomMapper roomMapper;
    private final MeetingRoomBookingMapper bookingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public ValidationResult validate(ApprovalForm form, ApprovalContext ctx) {
        List<String> errors = new ArrayList<>();
        Long roomId = parseLong(form.get("roomId"));
        MeetingRoom room = roomId == null ? null : roomMapper.selectById(roomId);
        if (room == null || room.getEnabled() == null || room.getEnabled() != 1) {
            errors.add("会议室不存在或已停用");
        }

        LocalDateTime start = parseTime(form.getString("startTime"));
        LocalDateTime end = parseTime(form.getString("endTime"));
        if (start == null) {
            errors.add("开始时间格式不正确，应为 yyyy-MM-dd HH:mm");
        }
        if (end == null) {
            errors.add("结束时间格式不正确，应为 yyyy-MM-dd HH:mm");
        }
        if (start != null && end != null) {
            if (!end.isAfter(start)) {
                errors.add("结束时间必须晚于开始时间");
            } else {
                if (start.toLocalDate().isBefore(LocalDate.now())) {
                    errors.add("不能预约过去的会议室");
                }
                if (start.toLocalTime().isBefore(OPEN_TIME) || end.toLocalTime().isAfter(CLOSE_TIME)) {
                    errors.add("会议室开放时间为 08:00~22:00");
                }
            }
        }

        Integer attendees = parseInt(form.get("attendees"));
        if (attendees == null || attendees <= 0) {
            errors.add("参会人数必须大于 0");
        } else if (room != null && room.getCapacity() != null && attendees > room.getCapacity()) {
            errors.add("参会人数超过会议室容量 " + room.getCapacity());
        }

        String purpose = StrUtil.trim(form.getString("purpose"));
        if (StrUtil.isBlank(purpose)) {
            errors.add("会议用途不能为空");
        } else if (purpose.length() > 500) {
            errors.add("会议用途不能超过 500 字");
        }

        if (room != null && start != null && end != null && end.isAfter(start)) {
            MeetingRoomBooking conflict = findConflict(roomId, start, end, null);
            if (conflict != null) {
                errors.add("该时段已被预约：" + conflict.getStartTime() + " ~ " + conflict.getEndTime());
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    @Override
    public Long createBizRecord(ApprovalForm form, ApprovalContext ctx) {
        Long roomId = parseLong(form.get("roomId"));
        if (roomId == null) {
            throw new BusinessException(400, "会议室不能为空");
        }
        // 行锁串行化同一会议室的并发提交，锁后必须复核冲突
        MeetingRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null || room.getEnabled() == null || room.getEnabled() != 1) {
            throw new BusinessException(400, "会议室不存在或已停用");
        }
        LocalDateTime start = requireTime(form.getString("startTime"), "开始时间");
        LocalDateTime end = requireTime(form.getString("endTime"), "结束时间");
        if (!end.isAfter(start)) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        Integer attendees = parseInt(form.get("attendees"));
        int capacity = room.getCapacity() == null ? 0 : room.getCapacity();
        if (attendees == null || attendees <= 0 || attendees > capacity) {
            throw new BusinessException(400, "参会人数超过会议室容量");
        }
        MeetingRoomBooking conflict = findConflict(roomId, start, end, null);
        if (conflict != null) {
            throw new BusinessException(409, "该时段已被预约，请更换时间");
        }

        LocalDateTime now = LocalDateTime.now();
        MeetingRoomBooking booking = new MeetingRoomBooking();
        booking.setApplicantId(ctx.applicantId());
        booking.setRoomId(roomId);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setAttendees(attendees);
        booking.setPurpose(StrUtil.trim(form.getString("purpose")));
        booking.setEquipment(writeJson(parseAttachments(form.get("equipment"))));
        booking.setHasExternalGuest(parseBoolean(form.get("hasExternalGuest")) ? 1 : 0);
        booking.setApprovalStatus(ApprovalInstanceStatus.RUNNING.name());
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        bookingMapper.insert(booking);
        log.info("[会议室] 创建预约: id={}, room={}, applicant={}, {}~{}",
                booking.getId(), room.getName(), ctx.applicantId(), start, end);
        return booking.getId();
    }

    @Override
    public void bindInstance(Long bizId, Long instanceId) {
        bookingMapper.update(null, new LambdaUpdateWrapper<MeetingRoomBooking>()
                .eq(MeetingRoomBooking::getId, bizId)
                .set(MeetingRoomBooking::getApprovalInstanceId, instanceId)
                .set(MeetingRoomBooking::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public Map<String, Object> extractFacts(ApprovalForm form, ApprovalContext ctx) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Long roomId = parseLong(form.get("roomId"));
        LocalDateTime start = parseTime(form.getString("startTime"));
        LocalDateTime end = parseTime(form.getString("endTime"));
        MeetingRoom room = roomId == null ? null : roomMapper.selectById(roomId);
        facts.put("roomId", roomId);
        facts.put("roomName", room == null ? null : room.getName());
        facts.put("attendees", parseInt(form.get("attendees")));
        facts.put("capacity", room == null ? null : room.getCapacity());
        facts.put("hasExternalGuest", parseBoolean(form.get("hasExternalGuest")));
        if (start != null && end != null && end.isAfter(start)) {
            facts.put("startTime", start.toString());
            facts.put("endTime", end.toString());
            facts.put("hours", Duration.between(start, end).toMinutes() / 60.0);
            facts.put("conflict", findConflict(roomId, start, end, null) != null);
        }
        return facts;
    }

    @Override
    public Object loadBizDetail(ApprovalInstance instance) {
        return findByInstance(instance);
    }

    @Override
    public void applyResult(ApprovalInstance instance, ApprovalInstanceStatus result) {
        MeetingRoomBooking booking = findByInstance(instance);
        if (booking == null) {
            log.warn("[会议室] 未找到预约记录，跳过结果回调: instanceId={}", instance.getId());
            return;
        }
        bookingMapper.update(null, new LambdaUpdateWrapper<MeetingRoomBooking>()
                .eq(MeetingRoomBooking::getId, booking.getId())
                .set(MeetingRoomBooking::getApprovalStatus, result.name())
                .set(MeetingRoomBooking::getUpdatedAt, LocalDateTime.now()));
        log.info("[会议室] 结果回调: id={}, instanceId={}, status={}",
                booking.getId(), instance.getId(), result);
    }

    private MeetingRoomBooking findByInstance(ApprovalInstance instance) {
        if (instance.getBizId() != null && instance.getBizId() > 0) {
            MeetingRoomBooking byId = bookingMapper.selectById(instance.getBizId());
            if (byId != null) {
                return byId;
            }
        }
        return bookingMapper.selectOne(Wrappers.<MeetingRoomBooking>lambdaQuery()
                .eq(MeetingRoomBooking::getApprovalInstanceId, instance.getId())
                .last("LIMIT 1"));
    }

    private MeetingRoomBooking findConflict(Long roomId, LocalDateTime start,
                                            LocalDateTime end, Long excludeId) {
        List<MeetingRoomBooking> bookings = bookingMapper.selectList(
                Wrappers.<MeetingRoomBooking>lambdaQuery()
                        .eq(MeetingRoomBooking::getRoomId, roomId)
                        .in(MeetingRoomBooking::getApprovalStatus, "RUNNING", "APPROVED")
                        .ne(excludeId != null, MeetingRoomBooking::getId, excludeId));
        for (MeetingRoomBooking booking : bookings) {
            if (booking.getStartTime() == null || booking.getEndTime() == null) {
                continue;
            }
            if (start.isBefore(booking.getEndTime()) && end.isAfter(booking.getStartTime())) {
                return booking;
            }
        }
        return null;
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private LocalDateTime requireTime(String value, String label) {
        LocalDateTime time = parseTime(value);
        if (time == null) {
            throw new BusinessException(400, label + "格式不正确，应为 yyyy-MM-dd HH:mm");
        }
        return time;
    }

    private Long parseLong(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean parseBoolean(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> parseAttachments(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && StrUtil.isNotBlank(String.valueOf(item)))
                    .map(String::valueOf)
                    .toList();
        }
        if (value instanceof String text) {
            if (StrUtil.isBlank(text)) {
                return List.of();
            }
            try {
                List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
                return parsed == null ? List.of() : parsed;
            } catch (Exception e) {
                return List.of(text);
            }
        }
        return List.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "会议设备序列化失败");
        }
    }
}
