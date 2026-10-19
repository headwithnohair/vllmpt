package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的待办。
 */
@Data
public class TaskTodoVO {

    private Long taskId;

    private Long instanceId;

    private String title;

    private String bizType;

    private String nodeKey;

    private String nodeName;

    private Long applicantId;

    private LocalDateTime createdAt;
}
