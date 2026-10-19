package org.albedo.vllmpt.module.approval.core.engine.assignee;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAssigneeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审批人解析器注册表。
 * <p>
 * 同时负责解析抄送配置：
 * <pre>
 * [{"type":"ROLE","roleCode":"HR"},{"type":"USER","userIds":[2,3]}]
 * </pre>
 */
@Component
public class AssigneeResolverRegistry {

    private final Map<ApprovalAssigneeType, AssigneeResolver> resolvers;
    private final ObjectMapper objectMapper;

    public AssigneeResolverRegistry(List<AssigneeResolver> resolverList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resolvers = new EnumMap<>(ApprovalAssigneeType.class);
        resolverList.forEach(resolver -> this.resolvers.put(resolver.type(), resolver));
    }

    public List<Long> resolve(String assigneeType, String configJson,
                              ApprovalInstance instance, Map<String, Object> facts) {
        return resolve(new AssigneeSpec(parseType(assigneeType), configJson), instance, facts);
    }

    public List<Long> resolve(AssigneeSpec spec, ApprovalInstance instance, Map<String, Object> facts) {
        AssigneeResolver resolver = resolvers.get(spec.type());
        if (resolver == null) {
            throw new BusinessException(400, "不支持的审批人类型: " + spec.type());
        }
        return resolver.resolve(spec, instance, facts).stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 解析抄送节点配置，返回可交给解析器执行的声明列表。
     */
    public List<AssigneeSpec> parseCcSpecs(String ccConfig) {
        if (StrUtil.isBlank(ccConfig)) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(ccConfig);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "抄送配置不是合法 JSON");
        }

        List<JsonNode> items = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(items::add);
        } else if (root.has("receivers") && root.get("receivers").isArray()) {
            root.get("receivers").forEach(items::add);
        } else {
            items.add(root);
        }

        List<AssigneeSpec> specs = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw new BusinessException(400, "抄送配置必须是对象或对象数组");
            }
            String type = item.path("type").asText("");
            if (StrUtil.isBlank(type)) {
                throw new BusinessException(400, "抄送配置缺少 type");
            }
            ObjectNode config = ((ObjectNode) item).deepCopy();
            config.remove("type");
            try {
                specs.add(new AssigneeSpec(parseType(type), objectMapper.writeValueAsString(config)));
            } catch (JsonProcessingException e) {
                throw new BusinessException(400, "抄送配置解析失败");
            }
        }
        return specs;
    }

    private ApprovalAssigneeType parseType(String type) {
        if (StrUtil.isBlank(type)) {
            throw new BusinessException(400, "审批人类型不能为空");
        }
        try {
            return ApprovalAssigneeType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的审批人类型: " + type);
        }
    }
}
