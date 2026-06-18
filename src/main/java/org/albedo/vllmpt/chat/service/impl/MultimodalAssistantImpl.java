package org.albedo.vllmpt.chat.service.impl;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
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

        String sessionId=request.getSessionId();
        // 1. 获取记忆
        ChatMemory memory = memoryProvider.get(sessionId);

        // 2. 解析附件，获取多模态内容和用于记忆的纯文本
        MultimodalContentResolver.ResolveResult resolveResult = contentResolver.resolve(sessionId, request.getText(), request.getAttachments());
        List<Content> modelContents = resolveResult.contentsForModel;
        String currentMemoryText = resolveResult.memoryText;

        // 3. 组装最终发给模型的 UserMessage (包含历史、当前文本、图片)
        UserMessage userMessage = buildFinalUserMessage(memory, currentMemoryText, modelContents);
        log.info("发送给模型的消息结构: {}", userMessage.toString());
        log.info("发送消息结构: {}", request.toString());
        // 4. 通过 Factory 动态获取模型并调用
        ChatModel chatModel = chatModelFactory.createModel(request.getModelName(), request.getTemperature(), request.getMaxTokens());
        AiMessage aiMessage = chatModel.chat(userMessage).aiMessage();

        // 5. 更新记忆 (只存纯文本)
        memory.add(UserMessage.from(currentMemoryText));
        memory.add(aiMessage);

        return aiMessage.text();
    }


    /**
     * 私有方法：负责拼装最终的 UserMessage (屏蔽底层 API 细节)
     */
    private UserMessage buildFinalUserMessage(ChatMemory memory, String currentText, List<Content> modelContents) {

        String historyText = buildHistoryText(memory.messages());
        String finalUserText = historyText + "用户: " + currentText;
        log.info(memory.toString(),currentText);


        // 如果没有图片/文件，直接返回纯文本消息
        if (modelContents == null || modelContents.isEmpty()) {
            return UserMessage.from(finalUserText);
        }
        log.info(modelContents.toString());
        // 如果有图片/文件，组装多模态消息
        List<Content> finalContents = new ArrayList<>();
        finalContents.add(TextContent.from(finalUserText));
        finalContents.addAll(modelContents);

        return UserMessage.from(finalContents);
    }

    /**
     * 私有方法：格式化历史记忆
     */
    private String buildHistoryText(List<ChatMessage> history) {
        StringBuilder historyText = new StringBuilder();
        for (ChatMessage msg : history) {
            if (msg instanceof UserMessage) {
                // 注意：这里强转 UserMessage 并 toString 可能会带上图片信息，
                // 如果只想保留文本，建议提取 UserMessage 中的 TextContent
                historyText.append("用户: ").append(((UserMessage) msg).singleText()).append("\n");
            } else if (msg instanceof AiMessage) {
                historyText.append("助手: ").append(((AiMessage) msg).text()).append("\n");
            }
        }
        return historyText.toString();
    }
}