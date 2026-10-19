package org.albedo.vllmpt.module.approval.core.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模板节点请求。
 */
@Data
public class TemplateNodeRequest {

    @NotBlank(message = "nodeKey 不能为空")
    private String nodeKey;

    @NotBlank(message = "nodeName 不能为空")
    private String nodeName;

    /** START / APPROVAL / AI_DECISION / CC / CONDITION / END */
    @NotBlank(message = "nodeType 不能为空")
    private String nodeType;

    /** 不传时按数组顺序自动编号 */
    private Integer sortOrder;

    /** USER / ROLE / DIRECT_MANAGER / DEPT_HEAD / FORM_FIELD / INITIATOR */
    private String assigneeType;

    /** 审批人配置：{"userIds":[2,3]}、{"roleCode":"HR"}、{"field":"approverId"} */
    private Map<String, Object> assigneeConfig;

    /** ANY / ALL / SEQUENTIAL */
    private String approvalMode;

    /** 跳过条件：{"field":"days","op":"LTE","value":3} */
    private Map<String, Object> conditionJson;

    private String aiPolicyKey;

    private BigDecimal aiMinConfidence;

    private String aiOnApproveNodeKey;

    private String aiOnEscalateNodeKey;

    /** REJECT / NEXT_NODE */
    private String aiOnRejectAction;

    /** 抄送配置：[{"type":"ROLE","roleCode":"HR"}] */
    private List<Map<String, Object>> ccConfig;

    private Integer slaHours;

    private Boolean enabled;
}
