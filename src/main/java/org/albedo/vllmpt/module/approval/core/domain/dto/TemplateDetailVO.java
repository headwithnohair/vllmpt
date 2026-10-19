package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;

import java.util.List;

/**
 * 模板详情。
 */
@Data
@AllArgsConstructor
public class TemplateDetailVO {

    private ApprovalTemplate template;

    private List<ApprovalTemplateNode> nodes;
}
