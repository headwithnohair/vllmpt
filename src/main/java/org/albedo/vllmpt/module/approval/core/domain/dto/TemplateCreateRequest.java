package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建审批模板请求。
 * <p>
 * 模板只创建不修改，需要调整时用同一个 templateKey 创建新版本。
 */
@Data
public class TemplateCreateRequest {

    @NotBlank(message = "templateKey 不能为空")
    private String templateKey;

    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    /** 表单 Schema：{"fields":[{"name":"days","label":"天数","type":"NUMBER","required":true}]} */
    @NotNull(message = "formSchema 不能为空")
    private Map<String, Object> formSchema;

    private Boolean aiFillEnabled;

    @NotEmpty(message = "模板节点不能为空")
    @Valid
    private List<TemplateNodeRequest> nodes;
}
