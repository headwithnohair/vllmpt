package org.albedo.vllmpt.module.approval.core.engine.assignee;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 从表单事实中取审批人：{"field":"approverId"}。
 * <p>
 * 字段值可以是单个用户 ID，也可以是用户 ID 数组。
 */
@Component
public class FormFieldResolver implements AssigneeResolver {

    private final ObjectMapper objectMapper;

    public FormFieldResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.FORM_FIELD;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        JsonNode config = AssigneeConfig.parse(objectMapper, spec);
        String field = config.path("field").asText("");
        if (StrUtil.isBlank(field)) {
            throw new BusinessException(400, "表单审批人缺少 field 配置");
        }
        return AssigneeConfig.toIds(facts.get(field));
    }
}
