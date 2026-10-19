package org.albedo.vllmpt.module.approval.core.domain.dto;

import lombok.Data;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalCc;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;

import java.util.List;

/**
 * 审批实例详情。
 */
@Data
public class InstanceDetailVO {

    private ApprovalInstance instance;

    private String templateName;

    private List<ApprovalTask> tasks;

    private List<ApprovalRecord> records;

    private List<ApprovalCc> ccs;
}
