package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.chat.model.dto.MultimodalChatRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装阶段 —— 合并 system + 历史记忆 + 当前用户消息，并确定模型参数
 * 对应 chatWithMultipleFiles 中的 allMessages 组装与 createModel 参数准备
 */
@Slf4j
@Component
public class ChatLoadInfoStage implements ChatPipelineStage<ChatPipelineContext> {

    private static final SystemMessage DEFAULT_SYSTEM_MESSAGE = SystemMessage.from("""
            你是一个智能助手，请根据你的知识回答用户问题。
            如果用户的问题超出了你的知识范围，请如实告知。
            """);

    @Override
    public void execute(ChatPipelineContext context) {

        // 1. 模型参数
        MultimodalChatRequest request = (MultimodalChatRequest) context.getAttribute("request");
        if (request != null) {
            context.setModelName(request.getModelName());
            context.setTemperature(request.getTemperature());
            context.setMaxTokens(request.getMaxTokens());
        }
        if (!StringUtils.hasText(context.getModelName())) {
            context.setModelName(asText(context.getAttribute("modelId")));
        }
        if (!StringUtils.hasText(context.getModelName())) {
            context.interrupt("未指定模型名称");
            return;
        }

        // 2. 用户消息必须有
        UserMessage userMessage = context.getUserMessage();
        if (userMessage == null) {
            context.interrupt("用户消息缺失");
            return;
        }

        // 3. 合并：系统提示词 + 历史记忆 + 当前用户输入
        SystemMessage systemMessage =
                context.getSystemMessage() != null ? context.getSystemMessage() : DEFAULT_SYSTEM_MESSAGE;

        ChatMemory memory = context.getRawChatMemory();

        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(systemMessage);
        if (memory != null) {
            allMessages.addAll(memory.messages());
        }
        allMessages.add(userMessage);

        context.setAllMessages(allMessages);
        log.debug("上下文组装完成，共 {} 条消息", allMessages.size());
    }

    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.error("上下文组装失败", e);
        context.interrupt("上下文组装失败：" + e.getMessage());
    }
}
