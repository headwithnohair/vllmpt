package org.albedo.vllmpt.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案 sys_user_profile。
 * <p>
 * 不直接改 sys_user，避免影响现有登录/配额逻辑。
 */
@Data
@TableName("sys_user_profile")
public class SysUserProfile {

    /** 与 sys_user.id 一致 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    private Long deptId;

    /** 直属主管 sys_user.id */
    private Long managerId;

    private LocalDate hireDate;

    /** PROBATION / REGULAR / LEFT */
    private String employmentStatus;

    private String jobLevel;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
