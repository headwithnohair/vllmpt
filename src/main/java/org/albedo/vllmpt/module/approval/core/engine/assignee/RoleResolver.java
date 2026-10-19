package org.albedo.vllmpt.module.approval.core.engine.assignee;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.albedo.vllmpt.module.user.mapper.SysUserRoleMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUserRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按角色解析：{"roleCode":"HR"} 或 {"roleCodes":["HR","FINANCE"]}。
 */
@Component
public class RoleResolver implements AssigneeResolver {

    private final ObjectMapper objectMapper;
    private final SysUserRoleMapper userRoleMapper;

    public RoleResolver(ObjectMapper objectMapper, SysUserRoleMapper userRoleMapper) {
        this.objectMapper = objectMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.ROLE;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        JsonNode config = AssigneeConfig.parse(objectMapper, spec);
        List<String> roleCodes = new ArrayList<>();
        if (StrUtil.isNotBlank(config.path("roleCode").asText())) {
            roleCodes.add(config.get("roleCode").asText());
        }
        JsonNode multi = config.get("roleCodes");
        if (multi != null && multi.isArray()) {
            multi.forEach(node -> {
                if (StrUtil.isNotBlank(node.asText())) {
                    roleCodes.add(node.asText());
                }
            });
        }
        if (roleCodes.isEmpty()) {
            throw new BusinessException(400, "角色审批人缺少 roleCode 配置");
        }

        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .in(SysUserRole::getRoleCode, roleCodes))
                .stream()
                .map(SysUserRole::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
