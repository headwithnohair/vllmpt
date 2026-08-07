package org.albedo.vllmpt.module.chat.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

import java.util.*;

/**
 * 工具注册表 —— 统一管理所有工具的注册、检索、分组
 */
@Slf4j
public class ToolRegistry {

    // key: 工具名称, value: 工具实例
    private final Map<String, DefaultToolExecutor> executors = new LinkedHashMap<>();
    // key: 分组标签, value: 该组下的工具名称
    private final Map<String, Set<String>> groupIndex = new LinkedHashMap<>();

    /** 注册单个工具，可带分组 */
    public ToolRegistry register(DefaultToolExecutor toolObj, String... groups) {
        String name = toolObj.getClass().getSimpleName();
        executors.put(name, toolObj);
        for (String g : groups) {
            groupIndex.computeIfAbsent(g, k -> new LinkedHashSet<>()).add(name);
        }
        return this;
    }

    /** 批量注册（Spring 场景下自动扫描） */
    public ToolRegistry registerAll(Collection<AiToolProvider> tools, String defaultGroup) {
        tools.forEach(t -> register((DefaultToolExecutor) t, defaultGroup));
        return this;
    }

    /** 按分组取工具实例 */
    public List<DefaultToolExecutor> getByGroup(String group) {
        Set<String> names = groupIndex.getOrDefault(group, Set.of());
        return names.stream()
                .map(executors::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 取全部 */
    public List<Object> getAll() {
        return new ArrayList<>(executors.values());
    }

    /** 生成 ToolSpecification 列表（核心转换） */
    public List<ToolSpecification> toSpecs(List<Object> tools) {
        return tools.stream()
                .flatMap(t -> ToolSpecifications.toolSpecificationsFrom(t.getClass()).stream())
                .toList();
    }

    public List<ToolSpecification> allSpecs() {
        return executors.values().stream()
                .flatMap(obj -> ToolSpecifications
                        .toolSpecificationsFrom(obj).stream())
                .toList();
    }

    /** 核心执行：根据 LLM 请求调用工具（极简一行） */
    public String execute(ToolExecutionRequest request) {
        String methodName = request.name();
        DefaultToolExecutor executor = executors.get(methodName);

        if (executor == null) {
            throw new IllegalArgumentException("未找到工具: " + methodName);
        }

        // 直接执行，自动完成 JSON 参数 -> Java 方法参数 的绑定
     //   return executor.execute(request);
        return "待实现";
    }

    /** ★ 安全执行（推荐）：捕获异常，返回友好错误信息给 LLM */
    public String executeSafely(ToolExecutionRequest request) {
        try {
            return execute(request);
        } catch (Exception e) {
            log.error("工具执行异常: {}", request.name(), e);
            // 返回错误描述，LLM 会看懂并告知用户，避免流程崩溃
            return "调用工具失败，原因：" + e.getMessage();
        }
    }
}