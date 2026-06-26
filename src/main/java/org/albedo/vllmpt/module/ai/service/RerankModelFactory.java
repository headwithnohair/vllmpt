package org.albedo.vllmpt.module.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RerankModelFactory {

    @Value("${langchain4j.open-ai.reranker-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.reranker-model.api-key}")
    private String apiKey;

    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    public ChatModel createModel(String modelName) {


        String cacheKey = String.format("%s:%.1f:%d", modelName);

        return modelCache.computeIfAbsent(cacheKey, k -> OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build());
    }
}
