package org.albedo.vllmpt.module.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatModelFactory {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamModelCache = new ConcurrentHashMap<>();


    public ChatModel createModel(String modelName, Double temperature, Integer maxTokens) {
        // 将 temperature 限制为 0.1 的倍数 (0.0, 0.1, 0.2 ... 1.0)
        double normalizedTemp = temperature != null ? Math.round(temperature * 10) / 10.0 : 0.7;

        // 将 maxTokens 限制为 1024 的倍数 (1024, 2048, 4096, 8192...)
        int normalizedTokens = maxTokens != null ? (maxTokens / 1024) * 1024 : 8192;

        String cacheKey = String.format("%s:%.1f:%d", modelName, normalizedTemp, normalizedTokens);

        return modelCache.computeIfAbsent(cacheKey, k -> OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(normalizedTemp)
                .maxTokens(normalizedTokens)
                .build());
    }

    public StreamingChatModel createStreamingModel(String modelName, Double temperature, Integer maxTokens) {

        // 将 temperature 限制为 0.1 的倍数 (0.0, 0.1, 0.2 ... 1.0)
        double normalizedTemp = temperature != null ? Math.round(temperature * 10) / 10.0 : 0.7;

        // 将 maxTokens 限制为 1024 的倍数 (1024, 2048, 4096, 8192...)
        int normalizedTokens = maxTokens != null ? (maxTokens / 1024) * 1024 : 8192;

        String cacheKey = String.format("%s:%.1f:%d", modelName, normalizedTemp, normalizedTokens);

        return streamModelCache.computeIfAbsent(cacheKey, k -> OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(normalizedTemp)
                .maxTokens(normalizedTokens)
                .build());
    }
}
