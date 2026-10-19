package org.albedo.vllmpt.module.approval.core.engine.assignee;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;

import java.util.List;
import java.util.Map;

/**
 * 审批人解析器。
 * <p>
 * 新增解析方式（如项目负责人、岗位）时只需要新增实现。
 */
public interface AssigneeResolver {

    ApprovalAssigneeType type();

    List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts);
}
