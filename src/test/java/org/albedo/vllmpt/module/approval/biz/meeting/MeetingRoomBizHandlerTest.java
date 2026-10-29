package org.albedo.vllmpt.module.approval.biz.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoom;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoomBooking;
import org.albedo.vllmpt.module.approval.biz.meeting.mapper.MeetingRoomBookingMapper;
import org.albedo.vllmpt.module.approval.biz.meeting.mapper.MeetingRoomMapper;
import org.albedo.vllmpt.module.approval.biz.meeting.service.MeetingRoomBizHandler;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingRoomBizHandlerTest {

    private MeetingRoomMapper roomMapper;
    private MeetingRoomBookingMapper bookingMapper;
    private MeetingRoomBizHandler handler;

    @BeforeEach
    void setUp() {
        roomMapper = mock(MeetingRoomMapper.class);
        bookingMapper = mock(MeetingRoomBookingMapper.class);
        handler = new MeetingRoomBizHandler(roomMapper, bookingMapper, new ObjectMapper());
        when(roomMapper.selectById(1L)).thenReturn(room(10));
        when(roomMapper.selectByIdForUpdate(1L)).thenReturn(room(10));
        when(bookingMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void acceptsAvailableRoom() {
        ValidationResult result = handler.validate(new ApprovalForm("MEETING_ROOM", form(5)), ctx());
        assertTrue(result.valid(), () -> String.join("；", result.errors()));
    }

    @Test
    void rejectsCapacityOverflow() {
        ValidationResult result = handler.validate(new ApprovalForm("MEETING_ROOM", form(20)), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("容量")));
    }

    @Test
    void rejectsTimeConflict() {
        MeetingRoomBooking existing = new MeetingRoomBooking();
        existing.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0));
        existing.setEndTime(LocalDateTime.now().plusDays(2).withHour(16).withMinute(0));
        when(bookingMapper.selectList(any())).thenReturn(List.of(existing));

        ValidationResult result = handler.validate(new ApprovalForm("MEETING_ROOM", form(5)), ctx());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("已被预约")));
    }

    @Test
    void createsBookingWithRoomLock() {
        when(bookingMapper.insert(any(MeetingRoomBooking.class))).thenAnswer(invocation -> {
            MeetingRoomBooking booking = invocation.getArgument(0);
            booking.setId(7L);
            return 1;
        });

        Long id = handler.createBizRecord(new ApprovalForm("MEETING_ROOM", form(5)), ctx());

        assertEquals(7L, id);
        verify(roomMapper).selectByIdForUpdate(1L);
        verify(bookingMapper).insert(any(MeetingRoomBooking.class));
    }

    private MeetingRoom room(int capacity) {
        MeetingRoom room = new MeetingRoom();
        room.setId(1L);
        room.setName("301 会议室");
        room.setCapacity(capacity);
        room.setEnabled(1);
        return room;
    }

    private Map<String, Object> form(int attendees) {
        Map<String, Object> form = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        form.put("roomId", 1L);
        form.put("startTime", LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).format(formatter));
        form.put("endTime", LocalDateTime.now().plusDays(2).withHour(16).withMinute(0).format(formatter));
        form.put("attendees", attendees);
        form.put("purpose", "方案评审");
        form.put("equipment", List.of("投影"));
        form.put("hasExternalGuest", false);
        return form;
    }

    private ApprovalContext ctx() {
        return new ApprovalContext(1L, "MEETING_ROOM", null, Map.of());
    }
}
