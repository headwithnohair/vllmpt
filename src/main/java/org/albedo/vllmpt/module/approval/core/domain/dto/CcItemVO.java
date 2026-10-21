package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抄送给我的列表项。
 */
@Data
public class CcItemVO {

    private Long id;

    private Long instanceId;

    private String instanceNo;

    private String title;

    private String bizType;

    /** 实例当前状态：RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED */
    private String status;

    private Long applicantId;

    private String nodeKey;

    /** 0=未读 1=已读 */
    private Integer readStatus;

    private LocalDateTime createdAt;
}
