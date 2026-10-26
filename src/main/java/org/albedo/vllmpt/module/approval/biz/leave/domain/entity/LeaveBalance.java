package org.albedo.vllmpt.module.approval.biz.leave.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期余额 leave_balance。
 * <p>
 * 只有年假、调休使用余额；病假/事假不产生余额锁定。
 * 扣减统一走 {@code LeaveBalanceService} 的条件更新，避免并发超卖。
 */
@Data
@TableName("leave_balance")
public class LeaveBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** ANNUAL / COMP_TIME */
    private String leaveType;

    private Integer year;

    private BigDecimal totalDays;

    private BigDecimal usedDays;

    private BigDecimal lockedDays;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
