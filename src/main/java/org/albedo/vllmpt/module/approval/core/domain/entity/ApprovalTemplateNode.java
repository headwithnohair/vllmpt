package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批模板节点 approval_template_node。
 */
@Data
@TableName("approval_template_node")
public class ApprovalTemplateNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String nodeKey;

    private String nodeName;

    /** START / APPROVAL / AI_DECISION / CC / CONDITION / END */
    private String nodeType;

    private Integer sortOrder;

    /** USER / ROLE / DIRECT_MANAGER / DEPT_HEAD / FORM_FIELD / INITIATOR */
    private String assigneeType;

    /** 审批人配置 JSON */
    private String assigneeConfig;

    /** ANY / ALL / SEQUENTIAL */
    private String approvalMode;

    /** 节点跳过条件 JSON */
    private String conditionJson;

    private String aiPolicyKey;

    private BigDecimal aiMinConfidence;

    private String aiOnApproveNodeKey;

    private String aiOnEscalateNodeKey;

    /** REJECT / NEXT_NODE */
    private String aiOnRejectAction;

    /** 抄送配置 JSON */
    private String ccConfig;

    private Integer slaHours;

    /** 1=启用 0=停用 */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
