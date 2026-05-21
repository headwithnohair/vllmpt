package org.albedo.vllmpt.ai.model;

import lombok.Data;

@Data
public class ModelInfo {
    private String ModelName;
    private String ModelId;
    private String type;
    private String scenes;   // [ GENERAL, CUSTOMER_SERVICE, COPYWRITING ]
    private String pricePerToken;
    private String maxToken;
    private String avgResponseTime;
    private int priority;
    private boolean enabled;
}
