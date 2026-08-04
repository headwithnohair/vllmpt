package org.albedo.vllmpt.module.chat.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.springframework.context.annotation.Bean;

import java.util.*;

/**
 * 工具注册表 —— 统一管理所有工具的注册、检索、分组
 */
public class ToolRegistry {

    // key: 工具名称, value: 工具实例
    private final Map<String, Object> toolInstances = new LinkedHashMap<>();
    // key: 分组标签, value: 该组下的工具名称
    private final Map<String, Set<String>> groupIndex = new LinkedHashMap<>();

    /** 注册单个工具，可带分组 */
    public ToolRegistry register(Object toolObj, String... groups) {
        String name = toolObj.getClass().getSimpleName();
        toolInstances.put(name, toolObj);
        for (String g : groups) {
            groupIndex.computeIfAbsent(g, k -> new LinkedHashSet<>()).add(name);
        }
        return this;
    }

    /** 批量注册（Spring 场景下自动扫描） */
    public ToolRegistry registerAll(Collection<AiToolProvider> tools, String defaultGroup) {
        tools.forEach(t -> register(t, defaultGroup));
        return this;
    }

    /** 按分组取工具实例 */
    public List<Object> getByGroup(String group) {
        Set<String> names = groupIndex.getOrDefault(group, Set.of());
        return names.stream()
                .map(toolInstances::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 取全部 */
    public List<Object> getAll() {
        return new ArrayList<>(toolInstances.values());
    }

    /** 生成 ToolSpecification 列表（核心转换） */
    public List<ToolSpecification> toSpecs(List<Object> tools) {
        return tools.stream()
                .flatMap(t -> ToolSpecifications.toolSpecificationsFrom(t.getClass()).stream())
                .toList();
    }

    public List<ToolSpecification> allSpecs() {
        return toolInstances.values().stream()
                .flatMap(obj -> ToolSpecifications
                        .toolSpecificationsFrom(obj).stream())
                .toList();
    }
}