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

    @Value("${app.vector-store.collection-name}")
    private String collectionName;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(baseUrl)
                .collectionName(collectionName)
                // 生产环境建议关闭日志，或者使用 SLF4J 桥接
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
