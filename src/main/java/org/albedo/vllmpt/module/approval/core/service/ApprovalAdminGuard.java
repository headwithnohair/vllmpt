package org.albedo.vllmpt.module.approval.core.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.user.mapper.SysUserRoleMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUserRole;
import org.springframework.stereotype.Component;

/**
 * 审批管理端权限校验。
 * <p>
 * 当前身份仍由 X-User-Id 提供；接入真实登录态后只需替换用户上下文来源。
 * 模板创建/编辑/发布/下线要求 ADMIN 角色。
 */
@Component
@RequiredArgsConstructor
public class ApprovalAdminGuard {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SysUserRoleMapper userRoleMapper;

    public void requireAdmin() {
        Long userId = UserContextHolder.requireUserId();
        Long count = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleCode, ADMIN_ROLE));
        if (count == null || count == 0) {
            throw new BusinessException(403, "仅管理员可以管理审批模板");
        }
    }
}
