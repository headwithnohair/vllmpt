package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.Data;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;

import java.util.List;

/**
 * 审批任务详情：审批人处理时查看表单与历史意见。
 */
@Data
public class TaskDetailVO {

    private ApprovalTask task;

    private ApprovalInstance instance;

    private String templateName;

    /** 模板表单 Schema，前端据此渲染只读表单 */
    private String formSchema;

    private List<ApprovalRecord> records;
}
