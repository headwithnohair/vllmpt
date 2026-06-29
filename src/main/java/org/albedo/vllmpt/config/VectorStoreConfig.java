package org.albedo.vllmpt.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Value("${app.vector-store.base-url}")
    private String baseUrl;

    @Value("${app.vector-store.knowledge-base-collection}")
    private String knowledgeBaseCollection;

    @Value("${app.vector-store.user-profile-collection}")
    private String userProfileCollection;

    @Bean("knowledgeBaseEmbeddingStore")
    public EmbeddingStore<TextSegment> knowledgeBaseEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(baseUrl)
                .collectionName(knowledgeBaseCollection)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    //如果你必须要在底层剔除文本传输，你需要绕过 LangChain4j 的 EmbeddingStore 接口，直接使用 Chroma 的 Java Client 进行查询
    //以后再考虑
    @Bean("userProfileEmbeddingStore")
    public EmbeddingStore<TextSegment> userProfileEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(baseUrl)
                .collectionName(userProfileCollection)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
