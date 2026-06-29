package org.albedo.vllmpt.config;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import org.albedo.vllmpt.module.ai.service.RerankModelFactory;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankConfig {

    @Value("${langchain4j.open-ai.reranker-model.model-name}")
    private String modelName;

    @Autowired
    private RerankModelFactory rerankModelFactory;

    @Bean
    public ScoringModel reRankModel() {
        return rerankModelFactory.createModel(modelName);
    }

    @Bean
    public ContentAggregator contentAggregator(ScoringModel scoringModel) {
        return ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .maxResults(5)   // 重排后保留的文档数
                .minScore(0.7)   // 最低分数阈值
                .build();
    }

}
