package org.albedo.vllmpt.module.approval.core.engine.assignee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 指定用户：{"userIds":[2,3]} 或 {"userId":2}。
 */
@Component
public class UserResolver implements AssigneeResolver {

    private final ObjectMapper objectMapper;

    public UserResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalAssigneeType type() {
        return ApprovalAssigneeType.USER;
    }

    @Override
    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        JsonNode config = AssigneeConfig.parse(objectMapper, spec);
        return AssigneeConfig.readIds(config, "userIds", "userId");
    }
}
