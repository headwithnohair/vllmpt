package org.albedo.vllmpt.module.approval.core.domain.enums;

/**
 * 审批模板状态。
 * <p>
 * 模板一旦发布就不再修改，需要调整时基于 template_key 新建下一个版本，
 * 运行中的实例始终绑定创建时的 template_id。
 */
public enum ApprovalTemplateStatus {
    DRAFT,
    PUBLISHED,
    DISABLED
}
