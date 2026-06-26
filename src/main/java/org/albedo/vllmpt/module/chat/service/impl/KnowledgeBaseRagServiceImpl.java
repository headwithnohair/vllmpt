package org.albedo.vllmpt.module.chat.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
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
public class KnowledgeBaseRagServiceImpl   {


    @Value("${langchain4j.open-ai.embedding-model.dimension}")
    private Integer dimension;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    private final EmbeddingStore<TextSegment> knowledgeBaseEmbeddingStore;

    public KnowledgeBaseRagServiceImpl(
            @Qualifier("knowledgeBaseEmbeddingStore")EmbeddingStore<TextSegment> knowledgeBaseEmbeddingStore ) {
        this.knowledgeBaseEmbeddingStore = knowledgeBaseEmbeddingStore;
    }


    public void indexText(String text) {

        EmbeddingModel model = embeddingModelFactory.createModel(modelName, dimension);
        // 2. 调用模型进行向量化
        Embedding embedding = model.embed(text).content();

        // 3. 调用 Store 存入数据库
        knowledgeBaseEmbeddingStore.add(embedding, TextSegment.from(text));
    }

    public List<String> searchRelevantTexts(String query, int maxResults,Double minScore) {
        EmbeddingModel model = embeddingModelFactory.createModel(modelName, dimension);
        Embedding queryEmbedding= model.embed(query).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(minScore)
                .maxResults(maxResults)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = knowledgeBaseEmbeddingStore.search(embeddingSearchRequest).matches();
        List<String> results = matches.stream().map(item->item.embedded().text()).toList();
        EmbeddingMatch<TextSegment> embeddingMatch = matches.getFirst();
        System.out.println(embeddingMatch.score()); // 0.8144288493114709
        System.out.println(embeddingMatch.embedded().text());
        return  results;
    }
}
