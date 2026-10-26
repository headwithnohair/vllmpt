package org.albedo.vllmpt.module.chat.tool;

import org.albedo.vllmpt.common.exception.BusinessException;

/**
 * 工具执行上下文持有者（线程级）。
 * <p>
 * 生命周期由 {@code ChatAgentStage} 管理，工具方法只在一次 ReAct 循环内读取。
 */
public final class ToolContextHolder {

    private static final ThreadLocal<ToolContext> CURRENT = new ThreadLocal<>();

    private ToolContextHolder() {
    }

    public static void set(ToolContext context) {
        CURRENT.set(context);
    }

    public static ToolContext get() {
        return CURRENT.get();
    }

    public static ToolContext require() {
        ToolContext context = CURRENT.get();
        if (context == null) {
            throw new BusinessException(400, "缺少工具执行上下文，请通过对话入口调用");
        }
        return context;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
