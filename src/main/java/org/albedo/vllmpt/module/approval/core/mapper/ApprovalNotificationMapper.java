package org.albedo.vllmpt.module.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ApprovalNotificationMapper extends BaseMapper<ApprovalNotification> {

    /**
     * 幂等写入通知：notify_key 冲突时忽略。
     */
    @Insert("""
            INSERT IGNORE INTO approval_notification
                (notify_key, instance_id, task_id, receiver_id, notify_type, channel,
                 title, content, status, retry_count, sent_at, created_at)
            VALUES
                (#{notifyKey}, #{instanceId}, #{taskId}, #{receiverId}, #{type}, #{channel},
                 #{title}, #{content}, #{status}, 0, #{sentAt}, #{createdAt})
            """)
    int insertIgnore(@Param("notifyKey") String notifyKey,
                     @Param("instanceId") Long instanceId,
                     @Param("taskId") Long taskId,
                     @Param("receiverId") Long receiverId,
                     @Param("type") String type,
                     @Param("channel") String channel,
                     @Param("title") String title,
                     @Param("content") String content,
                     @Param("status") String status,
                     @Param("sentAt") LocalDateTime sentAt,
                     @Param("createdAt") LocalDateTime createdAt);
}
