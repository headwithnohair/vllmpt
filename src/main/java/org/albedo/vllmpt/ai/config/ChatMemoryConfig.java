package org.albedo.vllmpt.ai.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.albedo.vllmpt.ai.service.RedissonChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {
    /**
     * 提供 ChatMemoryProvider Bean
     * Spring 会自动将上面的 RedissonChatMemoryStore 注入进来
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedissonChatMemoryStore redissonChatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10) // 保留最近 10 条原始对话，超出的让大模型总结前5条 成摘要 (总结后为6条)
                .chatMemoryStore(redissonChatMemoryStore) // 绑定 Redis 持久化
                .build();
    }
}
