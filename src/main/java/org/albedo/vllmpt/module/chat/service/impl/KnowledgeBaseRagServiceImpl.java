package org.albedo.vllmpt.module.chat.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.albedo.vllmpt.module.ai.service.EmbeddingModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseRagServiceImpl   {


    @Value("${langchain4j.open-ai.embedding-model.dimension}")
    private Integer dimension;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;


    @Autowired
    private ContentAggregator contentAggregator ;

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

    public List<Content> searchRelevantTexts(String query, int maxResults,Double minScore) {
        EmbeddingModel model = embeddingModelFactory.createModel(modelName, dimension);
        Embedding queryEmbedding= model.embed(query).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(minScore)
                .maxResults(maxResults)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = knowledgeBaseEmbeddingStore.search(embeddingSearchRequest).matches();
        List<Content> contents = matches.stream()
                .map(match -> Content.from(match.embedded())) // TextSegment 可以直接转换
                .toList();

        Query text = Query.from(query);
        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        // 将你的 contents 列表放入一个 Collection 中

        queryToContents.put(text, List.of(contents));

        return contentAggregator.aggregate(queryToContents);
    }
}
