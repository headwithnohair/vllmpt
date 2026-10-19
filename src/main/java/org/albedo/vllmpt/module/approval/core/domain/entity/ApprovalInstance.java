package org.albedo.vllmpt.module.approval.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例 approval_instance。
 */
@Data
@TableName("approval_instance")
public class ApprovalInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String instanceNo;

    private String bizType;

    /** 业务表主键；通用提交时可以为 0 */
    private Long bizId;

    private Long templateId;

    private Integer templateVersion;

    private Long applicantId;

    private String title;

    /** 提交时的表单快照 JSON */
    private String formData;

    /** 扁平事实 JSON，供条件/AI 使用 */
    private String facts;

    /** DRAFT / RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED */
    private String status;

    private String currentNodeKey;

    private String currentNodeName;

    private Long parentInstanceId;

    private Integer submitCount;

    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;

    /** 乐观锁版本 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
