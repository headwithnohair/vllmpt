package org.albedo.vllmpt.chat.service.impl;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.ai.service.ChatModelFactory;
import org.albedo.vllmpt.ai.service.EmbeddingModelFactory;
import org.albedo.vllmpt.ai.service.MultimodalContentResolver;
import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.chat.service.MultimodalAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态助手（支持图片+文本）
 */
@Service
@Slf4j
public class MultimodalAssistantImpl implements MultimodalAssistant {


    @Autowired
    private ChatMemoryProvider memoryProvider;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Autowired
    private MultimodalContentResolver contentResolver;

    /**
     * 处理多张图片 + 文本
     */

    @Override
    public String chatWithMultipleFiles(MultimodalChatRequest request) {

        String sessionId = request.getSessionId();
        // 1. 获取记忆
        ChatMemory memory = memoryProvider.get(sessionId);

        // 2. 解析附件，获取多模态内容和用于记忆的纯文本
        MultimodalContentResolver.ResolveResult resolveResult = contentResolver.resolve(sessionId, request.getText(), request.getAttachments());
        List<Content> currentContents = new ArrayList<>();
        currentContents.add(TextContent.from(resolveResult.memoryText));
        log.info("resolveResult.memoryText:{}",resolveResult.memoryText);
        currentContents.addAll(resolveResult.contentsForModel);
        log.info("resolveResult.contentsForModel:{}",resolveResult.contentsForModel);
        UserMessage currentUserMsg = UserMessage.from(currentContents);

        // 3. 合并历史消息（历史 + 当前）
        List<ChatMessage> allMessages = new ArrayList<>(memory.messages());
        allMessages.add(currentUserMsg);

        // 4. 调用模型（动态创建，支持多轮）
        ChatModel chatModel = chatModelFactory.createModel(request.getModelName(), request.getTemperature(), request.getMaxTokens());
        AiMessage aiMessage = chatModel.chat(allMessages).aiMessage();

        // 5. 更新记忆（只存纯文本摘要）
        memory.add(UserMessage.from(resolveResult.memoryText));
        memory.add(aiMessage);


        return aiMessage.text();
    }

    @Override
    public TokenStream chatStream(MultimodalChatRequest request) {
        return null;
    }

}
