package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 保存审批填表草稿请求。
 */
@Data
public class ApprovalDraftRequest {

    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    /** 会话 ID，可选；用于区分同一用户在不同会话中的草稿 */
    private String sessionId;

    @NotNull(message = "formData 不能为空")
    private Map<String, Object> formData;
}
