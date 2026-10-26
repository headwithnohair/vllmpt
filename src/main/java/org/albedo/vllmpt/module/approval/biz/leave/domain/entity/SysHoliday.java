package org.albedo.vllmpt.module.approval.biz.leave.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 节假日/调休工作日 sys_holiday。
 * <p>
 * {@code dayType}：HOLIDAY（放假）/ WORKDAY（调休上班）。
 * 表为空时按周一至周五计算工作日。
 */
@Data
@TableName("sys_holiday")
public class SysHoliday {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate holidayDate;

    /** HOLIDAY / WORKDAY */
    private String dayType;

    private String name;

    private LocalDateTime createdAt;
}
