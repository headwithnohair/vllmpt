package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatLoadInfoStage implements ChatPipelineStage<ChatPipelineContext> {


    @Autowired
    private ChatModelFactory chatModelFactory;
    @Override
    public void execute(ChatPipelineContext context) {

        List<ChatMessage> allMessages = new ArrayList<>();

        SystemMessage systemMessage=context.getSystemMessage();
        ChatMemory chatMemory=context.getRawChatMemory();
        UserMessage currentUserMsg=context.getUserMessage();
        allMessages.add(systemMessage);
        allMessages.addAll(chatMemory.messages());
        allMessages.add(currentUserMsg);
        //  调用模型（动态创建，支持多轮）
//        ChatModel chatModel = chatModelFactory.createModel(context.getModelName(), request.getTemperature(), request.getMaxTokens());


        //需要做审查
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
