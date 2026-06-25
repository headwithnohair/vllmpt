package org.albedo.vllmpt.module.ai.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RedissonChatMemoryStore implements ChatMemoryStore {
    private final RedissonClient redissonClient;
    private final String keyPrefix;

    // 通过构造函数注入 RedissonClient
    public RedissonChatMemoryStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.keyPrefix = "langchain4j:chat_memory:"; // 自定义 Redis Key 前缀，方便管理
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        RList<String> list = redissonClient.getList(keyPrefix + memoryId);
        return list.stream()
                .map(ChatMessageDeserializer::messageFromJson)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        RList<String> list = redissonClient.getList(keyPrefix + memoryId);

        // 将 ChatMessage 列表序列化为 JSON 字符串列表
        List<String> jsonMessages = messages.stream()
                .map(ChatMessageSerializer::messageToJson)
                .toList();

        // 覆盖写入最新的消息列表
        list.clear();
        list.addAll(jsonMessages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redissonClient.getList(keyPrefix + memoryId).delete();
    }
}
