package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.springframework.stereotype.Component;

/**
 * 记忆落库阶段 —— 把本轮问答写入短期记忆，并输出最终响应
 * 对应 chatWithMultipleFiles 中的 memory.add(...) 与 return aiMessage.text()
 */
@Slf4j
@Component
public class ChatMemorySaveStage implements ChatPipelineStage<ChatPipelineContext> {

    @Override
    public void execute(ChatPipelineContext context) {

        AiMessage aiMessage = context.getFinalResult();
        if (aiMessage == null) {
            context.interrupt("模型未返回最终结果，未写入记忆");
            return;
        }

        ChatMemory memory = context.getRawChatMemory();
        if (memory != null) {
            String memoryText = context.getMemoryText() == null ? "" : context.getMemoryText();
            memory.add(UserMessage.from(memoryText));
            memory.add(aiMessage);
        }

        context.setAttribute("response", aiMessage.text());
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.error("写入会话记忆失败", e);
        context.interrupt("写入会话记忆失败：" + e.getMessage());
    }
}
