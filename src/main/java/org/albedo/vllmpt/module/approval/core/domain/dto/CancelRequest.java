package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 撤回审批请求。
 */
@Data
public class CancelRequest {

    @Size(max = 500, message = "撤回原因不能超过 500 字")
    private String reason;
}
