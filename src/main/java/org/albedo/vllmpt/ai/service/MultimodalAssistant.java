package org.albedo.vllmpt.ai.service;

import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.file.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态助手（支持图片+文本）
 */
@Service
@Slf4j
public class MultimodalAssistant {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired(required = false)
    private FileUploadService fileUploadService;

    @Autowired
    private RedissonChatMemoryStore store;

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;
    public String chatWithImage(String sessionId,String text,String imageUrl){
        log.info("处理多模态请求 - 文本: {}, 图片: {}", text, imageUrl);
        ChatMemory chatMemory = chatMemoryProvider.get(sessionId);
        // 直接使用原始 URL（不转存到 MinIO）
        // 原因：MinIO 的 localhost URL 云端 API 无法访问
        UserMessage message = UserMessage.from(
                TextContent.from(text),
                ImageContent.from(imageUrl)
        );
        chatMemory.add(message);
        List<ChatMessage> allMessages = chatMemory.messages();
        AiMessage   assistantMessage = chatModel.generate(allMessages).content();
        chatMemory.add(assistantMessage);
        String response =assistantMessage.text();

        log.info("多模态响应完成");
        return response;
    }

    /**
     * 处理多张图片 + 文本
     */
    public String chatWithMultipleImages(String sessionId,String text, List<String> imageUrls) {
        log.info("处理多模态请求 - 文本: {}, 图片: {}", text, imageUrls);
        TextContent textContent = TextContent.from(text);

        ChatMemory chatMemory=chatMemoryProvider.get(sessionId);
        // 直接使用原始 URL 列表（不转存到 MinIO）
        List<ImageContent> imageContentList = imageUrls.stream()
                .map(ImageContent::from)
                .toList();

        // 将所有Content（文本+图片）组合成一个列表
        List<Content> contents = new ArrayList<>();
        contents.add(textContent);
        contents.addAll(imageContentList);

        // 用内容列表创建UserMessage
        UserMessage userMessage = UserMessage.from(contents);
        chatMemory.add(userMessage);

        List<ChatMessage> allMessages = chatMemory.messages();
        // 发送消息并获取回复
        String response =chatModel.generate(allMessages).content().text();
        chatMemory.add(AiMessage.from(response));
        log.info("多模态响应完成");
        return response;
    }
}