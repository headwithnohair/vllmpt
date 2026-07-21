package org.albedo.vllmpt.module.chat.service;

import org.albedo.vllmpt.module.chat.model.entity.ChatMessage;
import org.albedo.vllmpt.module.chat.model.entity.PipelineContext;

import java.util.List;

/**
 * 上下文处理阶段 —— 流水线的基本单元
 *
 * 设计原则：
 * 1. 无副作用：不修改输入列表，返回新列表
 * 2. 幂等性：相同输入多次执行结果一致（尽量）
 * 3. 可观测：通过 context.auditLog 记录变更
 */
public interface MessageStage {

    /** 阶段名称，用于日志和审计 */
    String name();

    /** 执行顺序，数值越小越先执行 */
    default int order() { return 100; }

    /** 是否跳过此阶段（动态开关） */
    default boolean shouldSkip(PipelineContext context) { return false; }

    /**
     * 核心处理逻辑
     * @param messages 当前消息列表（来自上一阶段输出）
     * @param context  流水线上下文（可读写元数据）
     * @return 处理后的新消息列表
     */
    List<ChatMessage> process(List<ChatMessage> messages, PipelineContext context);
}