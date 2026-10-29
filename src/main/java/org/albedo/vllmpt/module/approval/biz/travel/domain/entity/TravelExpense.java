package org.albedo.vllmpt.module.approval.biz.travel.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 差旅费申请业务表 travel_expense。
 */
@Data
@TableName("travel_expense")
public class TravelExpense {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long approvalInstanceId;

    private Long applicantId;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal estimatedAmount;

    private String currency;

    private String costCenter;

    private String reason;

    /** 发票/凭证 URL 列表 JSON */
    private String invoiceUrls;

    /** 冗余审批状态：RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED */
    private String approvalStatus;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
