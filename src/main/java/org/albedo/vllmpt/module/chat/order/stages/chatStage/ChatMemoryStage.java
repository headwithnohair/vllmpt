package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记忆阶段 —— 按 sessionId 取出该会话的短期记忆
 * 对应 chatWithMultipleFiles 中的 memoryProvider.get(sessionId)
 */
@Slf4j
@Component
public class ChatMemoryStage implements ChatPipelineStage<ChatPipelineContext> {

    @Autowired
    private ChatMemoryProvider memoryProvider;

    @Override
    public void execute(ChatPipelineContext context) {

        Object sessionIdObj = context.getAttribute("sessionId");
        String sessionId = sessionIdObj == null ? null : sessionIdObj.toString();

        if (!StringUtils.hasText(sessionId)) {
            context.interrupt("sessionId 缺失，无法获取会话记忆");
            return;
        }

        ChatMemory memory = memoryProvider.get(sessionId);
        context.setRawChatMemory(memory);
        log.debug("已加载会话 [{}] 的记忆，历史消息 {} 条", sessionId, memory.messages().size());
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.error("加载会话记忆失败", e);
        context.interrupt("加载会话记忆失败：" + e.getMessage());
    }
}
