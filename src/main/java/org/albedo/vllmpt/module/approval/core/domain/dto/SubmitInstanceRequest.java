package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 提交审批请求。
 * <p>
 * P0 由调用方直接提供 formData/facts；P1 接入业务插件后，facts 由
 * {@code ApprovalBizHandler.extractFacts} 生成，不再由前端传入。
 */
@Data
public class SubmitInstanceRequest {

    /** 优先按 templateKey 找最新已发布版本 */
    private String templateKey;

    /** templateKey 为空时按业务类型找最新已发布模板 */
    private String bizType;

    private String title;

    @NotNull(message = "formData 不能为空")
    private Map<String, Object> formData;

    private Map<String, Object> facts;
}
