package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程模板 approval_template。
 * <p>
 * 同一个 template_key 每次发布新版本都会新增一行，运行中的实例固定 template_id，
 * 因此修改模板不会影响已发起实例。
 */
@Data
@TableName("approval_template")
public class ApprovalTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateKey;

    private String bizType;

    private String name;

    private Integer version;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 表单字段定义 JSON */
    private String formSchema;

    private String description;

    /** 1=启用 AI 填表 0=关闭 */
    private Integer aiFillEnabled;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
