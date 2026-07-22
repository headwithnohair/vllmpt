package org.albedo.vllmpt.module.ai.model.dto;

import lombok.Data;

@Data
public class ModelInfo {
    private String modelName;
    private String modelId;
    private String type;
    private String scenes;   // [ GENERAL, CUSTOMER_SERVICE, COPYWRITING ]
    private String pricePerToken;
    private String maxToken;
    private String avgResponseTime;
    private int priority;
    private boolean enabled;
}
