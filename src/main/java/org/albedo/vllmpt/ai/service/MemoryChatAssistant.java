package org.albedo.vllmpt.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface MemoryChatAssistant {
    /**
     * 带记忆的对话
     * @param chatId 会话 ID（用于区分不同用户的对话）
     * @param userMessage 用户消息
     * @return AI 回复
     */
    @SystemMessage("你是一个专业的电商智能助手，记住之前的对话内容")
    String chat(@MemoryId String chatId, @UserMessage String userMessage);

    /**
     * 清除会话记忆
     */
    void clearMemory(@MemoryId String chatId);
}
