package org.albedo.vllmpt.ai.config;


import dev.langchain4j.store.memory.chat.redis.RedisChatMemoryStore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 会话记忆配置
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(RedissonClient redissonClient) {
        return RedisChatMemoryStore.builder()

                .build();
    }
}