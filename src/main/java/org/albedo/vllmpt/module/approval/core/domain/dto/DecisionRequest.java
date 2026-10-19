package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批同意/拒绝请求。
 */
@Data
public class DecisionRequest {

    @Size(max = 500, message = "审批意见不能超过 500 字")
    private String comment;
}
