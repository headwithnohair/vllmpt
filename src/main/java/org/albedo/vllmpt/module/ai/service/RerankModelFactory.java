package org.albedo.vllmpt.module.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RerankModelFactory {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;


    private final Map<String, ScoringModel> modelCache = new ConcurrentHashMap<>();

    public ScoringModel createModel(String modelName) {


        String cacheKey = String.format("%s", modelName);

        return modelCache.computeIfAbsent(cacheKey, k ->  CohereScoringModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build());
    }
}
