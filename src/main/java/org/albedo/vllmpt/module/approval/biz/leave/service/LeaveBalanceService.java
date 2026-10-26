package org.albedo.vllmpt.module.approval.biz.leave.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.biz.leave.domain.entity.LeaveBalance;
import org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType;
import org.albedo.vllmpt.module.approval.biz.leave.mapper.LeaveBalanceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 假期余额服务。
 * <p>
 * 状态流转：
 * <pre>
 * 提交  locked_days += days
 * 通过  used_days += days, locked_days -= days
 * 驳回/撤回  locked_days -= days
 * </pre>
 * 所有变更都是条件更新，并发提交不会超卖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceMapper balanceMapper;

    public BigDecimal available(Long userId, LeaveType type, int year) {
        LeaveBalance balance = find(userId, type, year);
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return nullToZero(balance.getTotalDays())
                .subtract(nullToZero(balance.getUsedDays()))
                .subtract(nullToZero(balance.getLockedDays()));
    }

    /**
     * 提交时锁定余额；余额不足直接拒绝。
     */
    public void lock(Long userId, LeaveType type, BigDecimal days, int year) {
        int rows = balanceMapper.update(null, new LambdaUpdateWrapper<LeaveBalance>()
                .eq(LeaveBalance::getUserId, userId)
                .eq(LeaveBalance::getLeaveType, type.name())
                .eq(LeaveBalance::getYear, year)
                .apply("(total_days - used_days - locked_days) >= {0}", days)
                .setSql(true, "locked_days = locked_days + {0}", days)
                .setSql(true, "version = version + 1")
                .set(LeaveBalance::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(400, type.label() + "余额不足，无法提交");
        }
    }

    /**
     * 审批通过：锁定转为已使用。
     *
     * @return false 表示余额记录或锁定额度不足，需由调用方回滚
     */
    public boolean approve(Long userId, LeaveType type, BigDecimal days, int year) {
        int rows = balanceMapper.update(null, new LambdaUpdateWrapper<LeaveBalance>()
                .eq(LeaveBalance::getUserId, userId)
                .eq(LeaveBalance::getLeaveType, type.name())
                .eq(LeaveBalance::getYear, year)
                .apply("locked_days >= {0}", days)
                .setSql(true, "used_days = used_days + {0}", days)
                .setSql(true, "locked_days = locked_days - {0}", days)
                .setSql(true, "version = version + 1")
                .set(LeaveBalance::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            log.error("[请假] 余额结算失败: userId={}, type={}, days={}, year={}",
                    userId, type, days, year);
        }
        return rows > 0;
    }

    /**
     * 驳回/撤回：释放锁定额度。
     *
     * @return false 表示没有可释放的锁定额度（历史数据或重复释放），仅记录日志
     */
    public boolean release(Long userId, LeaveType type, BigDecimal days, int year) {
        int rows = balanceMapper.update(null, new LambdaUpdateWrapper<LeaveBalance>()
                .eq(LeaveBalance::getUserId, userId)
                .eq(LeaveBalance::getLeaveType, type.name())
                .eq(LeaveBalance::getYear, year)
                .apply("locked_days >= {0}", days)
                .setSql(true, "locked_days = locked_days - {0}", days)
                .setSql(true, "version = version + 1")
                .set(LeaveBalance::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            log.warn("[请假] 释放余额失败（无锁定额度）: userId={}, type={}, days={}, year={}",
                    userId, type, days, year);
        }
        return rows > 0;
    }

    public List<LeaveBalance> list(Long userId, int year) {
        return balanceMapper.selectList(Wrappers.<LeaveBalance>lambdaQuery()
                .eq(LeaveBalance::getUserId, userId)
                .eq(LeaveBalance::getYear, year)
                .orderByAsc(LeaveBalance::getLeaveType));
    }

    private LeaveBalance find(Long userId, LeaveType type, int year) {
        return balanceMapper.selectOne(Wrappers.<LeaveBalance>lambdaQuery()
                .eq(LeaveBalance::getUserId, userId)
                .eq(LeaveBalance::getLeaveType, type.name())
                .eq(LeaveBalance::getYear, year)
                .last("LIMIT 1"));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
