package org.albedo.vllmpt.module.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalCc;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ApprovalCcMapper extends BaseMapper<ApprovalCc> {

    /**
     * 幂等写入抄送记录：唯一键冲突时直接忽略，重复进入节点不会产生重复抄送。
     */
    @Insert("""
            INSERT IGNORE INTO approval_cc
                (instance_id, node_key, receiver_id, channel, read_status, created_at)
            VALUES
                (#{instanceId}, #{nodeKey}, #{receiverId}, #{channel}, 0, #{createdAt})
            """)
    int insertIgnore(@Param("instanceId") Long instanceId,
                     @Param("nodeKey") String nodeKey,
                     @Param("receiverId") Long receiverId,
                     @Param("channel") String channel,
                     @Param("createdAt") LocalDateTime createdAt);
}
