package org.albedo.vllmpt.ai.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmbeddingModelFactory {

    // 硅基流动的 Embedding API 端点（与 Chat 不同）


    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private static  String EMBEDDING_BASE_URL ;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    private final Map<String,EmbeddingModel> modelCache = new ConcurrentHashMap<>();
    public EmbeddingModel createModel(String modelName, Integer dimension) {
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        int dim = (dimension != null) ? dimension : 1024;

        String cacheKey = String.format("%s:%d", modelName, dimension);
      return modelCache.computeIfAbsent(cacheKey,k -> OpenAiEmbeddingModel.builder()
              .baseUrl(EMBEDDING_BASE_URL)
              .apiKey(apiKey)
              .modelName(modelName)
              .dimensions(dim)
              .build());
    }
}
