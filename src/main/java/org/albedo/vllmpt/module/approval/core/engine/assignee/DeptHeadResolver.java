package org.albedo.vllmpt.module.approval.core.engine.assignee;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.albedo.vllmpt.module.user.mapper.SysDepartmentMapper;
import org.albedo.vllmpt.module.user.mapper.SysUserProfileMapper;
import org.albedo.vllmpt.module.user.model.entity.SysDepartment;
import org.albedo.vllmpt.module.user.model.entity.SysUserProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 部门负责人：申请人档案 → 部门 → leader_id。
 */
@Component
public class DeptHeadResolver implements AssigneeResolver {

    private final SysUserProfileMapper userProfileMapper;
    private final SysDepartmentMapper departmentMapper;

    public DeptHeadResolver(SysUserProfileMapper userProfileMapper, SysDepartmentMapper departmentMapper) {
        this.userProfileMapper = userProfileMapper;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.DEPT_HEAD;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        SysUserProfile profile = userProfileMapper.selectById(instance.getApplicantId());
        if (profile == null || profile.getDeptId() == null) {
            return List.of();
        }
        SysDepartment department = departmentMapper.selectById(profile.getDeptId());
        if (department == null || department.getLeaderId() == null) {
            return List.of();
        }
        return List.of(department.getLeaderId());
    }
}
