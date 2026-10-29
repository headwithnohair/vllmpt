package org.albedo.vllmpt.module.approval.biz.travel.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交差旅费申请。
 */
@Data
public class TravelSubmitRequest {

    @NotBlank(message = "目的地不能为空")
    private String destination;

    /** yyyy-MM-dd */
    @NotBlank(message = "开始日期不能为空")
    private String startDate;

    /** yyyy-MM-dd */
    @NotBlank(message = "结束日期不能为空")
    private String endDate;

    @NotNull(message = "预计金额不能为空")
    @DecimalMin(value = "0.01", message = "预计金额必须大于 0")
    private BigDecimal estimatedAmount;

    private String currency;

    private String costCenter;

    @NotBlank(message = "出差事由不能为空")
    @Size(max = 500, message = "出差事由不能超过 500 字")
    private String reason;

    private List<String> invoiceUrls;

    private String title;

    private String idempotencyKey;
}
