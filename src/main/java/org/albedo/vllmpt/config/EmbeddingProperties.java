package org.albedo.vllmpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {
    private String apiKey;
    private String modelName = "Qwen/Qwen3-VL-Embedding-8B";
    private String baseUrl = "https://api.siliconflow.cn/v1/embeddings";
    private int dimension = 1024;
}
