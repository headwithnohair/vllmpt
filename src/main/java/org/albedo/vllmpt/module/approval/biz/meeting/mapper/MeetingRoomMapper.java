package org.albedo.vllmpt.module.approval.biz.meeting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.biz.meeting.domain.entity.MeetingRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MeetingRoomMapper extends BaseMapper<MeetingRoom> {

    /**
     * 锁定会议室行，串行化同一会议室的并发预约。
     */
    @Select("SELECT * FROM meeting_room WHERE id = #{id} FOR UPDATE")
    MeetingRoom selectByIdForUpdate(@Param("id") Long id);
}
