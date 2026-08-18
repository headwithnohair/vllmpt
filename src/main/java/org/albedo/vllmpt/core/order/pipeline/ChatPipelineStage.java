package org.albedo.vllmpt.core.order.pipeline;

@FunctionalInterface
public interface ChatPipelineStage<C extends ChatPipelineContext> {

    /** 核心处理逻辑（唯一抽象方法） */
    void execute(C context);

    /** 阶段名称，默认取类名 */
    default String name() {
        return this.getClass().getSimpleName();
    }

    /** 是否执行（可动态跳过） */
    default boolean shouldExecute(C context) {
        return true;
    }

    /** 异常回调 */
    default void onError(C context, Throwable e) {
        throw new RuntimeException("Stage [" + name() + "] failed", e);
    }
}