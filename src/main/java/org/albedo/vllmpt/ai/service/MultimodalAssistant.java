package org.albedo.vllmpt.ai.service;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.chat.model.entity.Attachment;
import org.albedo.vllmpt.chat.model.entity.ProcessResult;
import org.albedo.vllmpt.chat.service.AttachmentProcessor;
import org.albedo.vllmpt.chat.service.impl.AttachmentProcessorRegistry;
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

    @Autowired
    private AttachmentProcessorRegistry processorRegistry;

    @Autowired
    private ChatMemoryProvider memoryProvider;

    /**
     * 处理多张图片 + 文本
     */

    public String chatWithMultipleFiles(String sessionId,String text, List<Attachment> attachments) {
        ChatMemory memory = memoryProvider.get(sessionId);

        // 1. 处理所有附件，收集 Content 和记忆文本
        List<Content> allContents = new ArrayList<>();
        StringBuilder memoryTextBuilder = new StringBuilder(text);

        for (Attachment att : attachments) {
            AttachmentProcessor processor = processorRegistry.getProcessor(att.getType());
            ProcessResult result = processor.process(att, sessionId);
            allContents.addAll(result.getContentsForModel());
            if (result.getMemoryText() != null) {
                memoryTextBuilder.append(" ").append(result.getMemoryText());
            }
            // 若需要向量化，这里可以触发异步任务，但通常 processor 内部已做
        }

        // 2. 加载历史记忆（纯文本）
        List<ChatMessage> history = memory.messages();
        String historyText = buildHistoryText(history);
        String finalUserText = historyText + "用户: " + memoryTextBuilder;

        // 3. 构造发送给模型的 UserMessage
        UserMessage userMessage;
        if (allContents.isEmpty()) {
            userMessage = UserMessage.from(finalUserText);
        } else {
            List<Content> finalContents = new ArrayList<>();
            finalContents.add(TextContent.from(finalUserText));
            finalContents.addAll(allContents);
            userMessage = UserMessage.from(finalContents);
        }
        log.info(userMessage.toString());
        // 4. 调用模型
        AiMessage aiMessage = chatModel.generate(userMessage).content();

        // 5. 存储记忆（只存纯文本）
        memory.add(UserMessage.from(memoryTextBuilder.toString()));
        memory.add(aiMessage);

        return aiMessage.text();
    }

    private String buildHistoryText(List<ChatMessage> history) {
        StringBuilder historyText = new StringBuilder();
        for (ChatMessage msg : history) {
            if (msg instanceof UserMessage) {
                historyText.append("用户: ").append((msg)).append("\n");
            } else if (msg instanceof AiMessage) {
                historyText.append("助手: ").append(((AiMessage) msg).text()).append("\n");
            }

        }
        return  historyText.toString();
    }
}