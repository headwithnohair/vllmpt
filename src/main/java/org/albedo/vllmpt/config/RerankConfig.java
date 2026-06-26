package org.albedo.vllmpt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankConfig {

    @Value("${langchain4j.open-ai.reranker-model.model-name}")
    private String modelName;


}
