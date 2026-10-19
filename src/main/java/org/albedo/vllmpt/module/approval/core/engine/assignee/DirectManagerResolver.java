package org.albedo.vllmpt.module.approval.core.engine.assignee;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.albedo.vllmpt.module.user.mapper.SysUserProfileMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUserProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 直属主管：读取 sys_user_profile.manager_id。
 */
@Component
public class DirectManagerResolver implements AssigneeResolver {

    private final SysUserProfileMapper userProfileMapper;

    public DirectManagerResolver(SysUserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.DIRECT_MANAGER;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        SysUserProfile profile = userProfileMapper.selectById(instance.getApplicantId());
        if (profile == null || profile.getManagerId() == null) {
            return List.of();
        }
        return List.of(profile.getManagerId());
    }
}
