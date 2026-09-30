package org.albedo.vllmpt.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 sys_user
 * <p>
 * 建表脚本：doc/sql/init_user_quota.sql
 * <p>
 * 注意：createdAt / updatedAt 由数据库默认值（DEFAULT CURRENT_TIMESTAMP）填充，
 * 实体里保持 null 即可 —— MyBatis-Plus 默认跳过 null 字段，不会把 null 写进 INSERT。
 * 项目里没有 MetaObjectHandler，所以不要声明自动填充注解。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 用户 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 密码哈希（不存明文） */
    private String passwordHash;

    /** 状态：1=启用 0=禁用 */
    private Integer status;

    /** 逻辑删除：0=正常 1=已删除（全局配置 logic-delete-field: deleted） */
    @TableLogic
    private Integer deleted;

    /** 创建时间（数据库默认值填充） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库默认值填充） */
    private LocalDateTime updatedAt;
}
