package org.albedo.vllmpt.module.chat.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具注册表 —— 统一管理所有工具的注册、检索、执行
 */
@Slf4j
public class ToolRegistry {

    /**
     * 核心映射：工具方法名 -> ToolExecutor
     * key 是大模型看到的工具名（即 @Tool 方法名），value 是对应的执行器
     */
    private final Map<String, ToolExecutor> executors = new LinkedHashMap<>();

    /**
     * 保留原始工具实例，用于生成 ToolSpecification
     * （因为 ToolSpecifications.toolSpecificationsFrom 需要原始对象）
     */
    private final List<Object> rawToolInstances = new ArrayList<>();

    /**
     * 分组索引：分组标签 -> 该组下的工具方法名集合
     */
    private final Map<String, Set<String>> groupIndex = new LinkedHashMap<>();

    // ==================== 注册 ====================

    /**
     * 注册单个工具实例（如 SimpleTool）
     * 内部会扫描所有 @Tool 方法，为每个方法创建一个 DefaultToolExecutor
     */
    public ToolRegistry register(Object toolInstance, String... groups) {
        rawToolInstances.add(toolInstance);
        Class<?> targetClass = ClassUtils.getUserClass(toolInstance.getClass());
        // 扫描该实例上所有带 @Tool 注解的方法
        // 扫描真实类上的方法
        for (Method method : targetClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }

            String toolName = method.getName();

            // 注意：这里传入的 toolInstance 依然是 Spring 注入的代理对象，
            // 但 method 是真实类的方法。CGLIB 代理是真实类的子类，
            // 所以 method.invoke(toolInstance, args) 是可以正常执行的。
            DefaultToolExecutor executor = new DefaultToolExecutor(toolInstance, method);
            executors.put(toolName, executor);

            // 建立分组索引
            for (String g : groups) {
                groupIndex.computeIfAbsent(g, k -> new LinkedHashSet<>()).add(toolName);
            }

            log.info("注册工具: {} -> {}.{}", toolName,
                    toolInstance.getClass().getSimpleName(), method.getName());
        }
        return this;
    }

    /**
     * 批量注册（Spring 场景：注入所有实现了某接口的工具 Bean）
     */
    public ToolRegistry registerAll(Collection<?> toolBeans, String defaultGroup) {
        toolBeans.forEach(bean -> register(bean, defaultGroup));
        return this;
    }

    // ==================== 生成 Specs ====================

    /**
     * 生成全部工具的 ToolSpecification 列表
     * 传给 ChatModel，让大模型知道有哪些工具可用
     */
    public List<ToolSpecification> allSpecs() {
        return rawToolInstances.stream()
                .flatMap(obj -> ToolSpecifications.toolSpecificationsFrom(obj).stream())
                .collect(Collectors.toList());
    }

    /**
     * 按分组生成 Specs（可选：不同场景暴露不同工具）
     */
    public List<ToolSpecification> specsByGroup(String group) {
        Set<String> names = groupIndex.getOrDefault(group, Set.of());
        // 过滤出属于该分组的工具实例，再生成 specs
        // 简化处理：生成全部后按名称过滤
        return allSpecs().stream()
                .filter(spec -> names.contains(spec.name()))
                .collect(Collectors.toList());
    }

    // ==================== 执行 ====================

    /**
     * 核心执行：根据大模型返回的 ToolExecutionRequest 调用对应工具
     *
     * 调用的是 ToolExecutor 接口的【公开方法】：
     * String execute(ToolExecutionRequest request, Object memoryId)
     */
    public String execute(ToolExecutionRequest request) {
        String toolName = request.name();
        ToolExecutor executor = executors.get(toolName);

        if (executor == null) {
            throw new IllegalArgumentException("未注册的工具: " + toolName);
        }

        // ★ 关键：调用 ToolExecutor 接口的公开方法，memoryId 传 null 即可
        return executor.execute(request, null);
    }

    /**
     * 安全执行（推荐在 ReAct 循环中使用）
     * 捕获一切异常，返回错误描述给大模型，避免流程中断
     */
    public String executeSafely(ToolExecutionRequest request) {
        try {
            log.info("执行工具: {} | 参数: {}", request.name(), request.arguments());
            String result = execute(request);
            log.info("工具返回: {}", result);
            return result;
        } catch (Exception e) {
            log.error("工具执行异常: {}", request.name(), e);
            return "工具 [" + request.name() + "] 执行失败，原因：" + e.getMessage();
        }
    }

    // ==================== 查询 ====================

    public boolean hasTool(String name) {
        return executors.containsKey(name);
    }

    public Set<String> allToolNames() {
        return Collections.unmodifiableSet(executors.keySet());
    }
}