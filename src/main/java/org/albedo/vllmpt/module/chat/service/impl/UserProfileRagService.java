package org.albedo.vllmpt.module.chat.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.albedo.vllmpt.module.ai.service.EmbeddingModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProfileRagService {


    @Value("${langchain4j.open-ai.embedding-model.dimension}")
    private Integer dimension;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    private final EmbeddingStore<TextSegment> userProfileEmbeddingStore;
    public UserProfileRagService(@Qualifier("userProfileEmbeddingStore") EmbeddingStore<TextSegment> userProfileEmbeddingStore) {
        this.userProfileEmbeddingStore = userProfileEmbeddingStore;
    }

    public void indexUserText(String text) {

        EmbeddingModel model = embeddingModelFactory.createModel(modelName, dimension);
        // 2. 调用模型进行向量化
        Embedding embedding = model.embed(text).content();

        // 3. 调用 Store 存入数据库
        userProfileEmbeddingStore.add(embedding, TextSegment.from(text));
    }

    public List<String> searchRelevantUserTexts(String query, int maxResults, Double minScore) {
        EmbeddingModel model = embeddingModelFactory.createModel(modelName, dimension);
        Embedding queryEmbedding= model.embed(query).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(minScore)
                .maxResults(maxResults)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = userProfileEmbeddingStore.search(embeddingSearchRequest).matches();

        List<String> results = matches.stream().map(item->item.embedded().text()).toList();
        return  results;
    }


}
