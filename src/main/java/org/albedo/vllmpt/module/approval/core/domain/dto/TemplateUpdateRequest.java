package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 编辑草稿模板请求。
 * <p>
 * 只允许编辑 DRAFT 状态：templateKey / bizType / version 不可改，
 * 节点按请求全量替换。
 */
@Data
public class TemplateUpdateRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "formSchema 不能为空")
    private Map<String, Object> formSchema;

    private Boolean aiFillEnabled;

    @NotEmpty(message = "模板节点不能为空")
    @Valid
    private List<TemplateNodeRequest> nodes;
}
