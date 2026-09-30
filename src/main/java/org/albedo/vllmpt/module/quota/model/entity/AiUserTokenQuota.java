package org.albedo.vllmpt.module.quota.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Token 配额表 ai_user_token_quota
 * <p>
 * 建表脚本：doc/sql/init_user_quota.sql
 * <p>
 * 这是「每日配额上限」的唯一权威来源：Redis 侧的预扣需要拿 {@code dailyLimit} 作为判断阈值。
 * {@code usedToday} / {@code usedMonth} 只是 MySQL 侧的对账冗余，
 * <b>不要</b>用它们做限额判断（热路径以 Redis 计数为准）。
 */
@Data
@TableName("ai_user_token_quota")
public class AiUserTokenQuota {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 每日 token 配额上限 */
    private Long dailyLimit;

    /** 每月 token 配额上限，0=不限制 */
    private Long monthlyLimit;

    /** 今日已用 token（对账冗余，非限额依据） */
    private Long usedToday;

    /** 本月已用 token（对账冗余，非限额依据） */
    private Long usedMonth;

    /** 是否启用配额：1=启用 0=不限制 */
    private Integer quotaEnabled;

    /** 配额生效开始时间，null=立即生效 */
    private LocalDateTime effectiveFrom;

    /** 配额生效结束时间，null=长期有效 */
    private LocalDateTime effectiveTo;

    /** 备注（如：活动赠额） */
    private String remark;

    /** 逻辑删除：0=正常 1=已删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间（数据库默认值填充） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库默认值填充） */
    private LocalDateTime updatedAt;
}
