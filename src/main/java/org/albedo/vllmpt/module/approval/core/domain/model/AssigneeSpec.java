package org.albedo.vllmpt.module.approval.core.domain.model;

import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;

/**
 * 审批人/抄送人声明。
 *
 * @param type       解析方式
 * @param configJson 解析配置，如 {"userIds":[1,2]}、{"roleCode":"HR"}
 */
public record AssigneeSpec(ApprovalAssigneeType type, String configJson) {
}
