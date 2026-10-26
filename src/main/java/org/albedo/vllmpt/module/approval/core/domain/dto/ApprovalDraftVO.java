package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批填表草稿。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDraftVO {

    private String bizType;

    private String sessionId;

    /** 已合并的表单 */
    private Map<String, Object> formData;

    /** 缺失的必填字段名 */
    private List<String> missingFields;

    private LocalDateTime updatedAt;
}
