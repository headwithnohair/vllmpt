package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.Data;

import java.util.Map;

/**
 * 驳回后重新提交请求。
 * <p>
 * 沿用原实例的模板与版本创建新实例，{@code parent_instance_id} 指向被驳回的旧实例；
 * 表单为空时沿用旧实例的表单快照。
 */
@Data
public class ResubmitInstanceRequest {

    /** 修改后的表单；为空表示按原表单重新提交 */
    private Map<String, Object> formData;

    private Map<String, Object> facts;

    private String title;

    /** 提交幂等键，语义同提交接口 */
    private String idempotencyKey;
}
