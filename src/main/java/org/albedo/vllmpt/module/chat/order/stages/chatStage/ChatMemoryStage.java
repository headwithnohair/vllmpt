package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ChatMemoryStage implements ChatPipelineStage<ChatPipelineContext> {
    //用于获取session ID 的 短期记忆


    @Autowired
    private ChatMemoryProvider memoryProvider;


    @Override
    public void execute(ChatPipelineContext context) {
        // 获取记忆
        String sessionId = context.getAttribute("sessionId").toString();
        ChatMemory memory = memoryProvider.get(sessionId);

        context.setAttribute("memory",memory);
    }

    @Override
    public String name() {
        return ChatPipelineStage.super.name();
    }

    @Override
    public boolean shouldExecute(ChatPipelineContext context) {
        return ChatPipelineStage.super.shouldExecute(context);
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        ChatPipelineStage.super.onError(context, e);
    }
}
