package org.albedo.vllmpt.ai.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
                .maxMessages(5) // 保留最近 5 条原始对话，超出的会自动让大模型总结成摘要
                .chatMemoryStore(redissonChatMemoryStore) // 绑定 Redis 持久化
                .build();
    }
}
