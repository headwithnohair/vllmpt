package org.albedo.vllmpt.module.approval.core.engine.assignee;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 发起人本人。
 */
@Component
public class InitiatorResolver implements AssigneeResolver {

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.INITIATOR;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        return instance.getApplicantId() == null ? List.of() : List.of(instance.getApplicantId());
    }
}
