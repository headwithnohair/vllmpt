package org.albedo.vllmpt.module.ai.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmbeddingModelFactory {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String embeddingBaseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    private final Map<String, EmbeddingModel> modelCache = new ConcurrentHashMap<>();

    public EmbeddingModel createModel(String modelName, Integer dimension) {

        int dim = (dimension != null) ? dimension : 1024;

        String cacheKey = String.format("%s:%d", modelName, dimension);
        return modelCache.computeIfAbsent(cacheKey, k -> OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .dimensions(dim)
                .build());
    }
}
