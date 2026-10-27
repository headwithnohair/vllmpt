package org.albedo.vllmpt.module.approval.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审批 AI 决策配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.ai")
public class ApprovalAiProperties {

    /** 总开关；关闭后 AI 决策一律转人工 */
    private boolean enabled = true;

    private String modelName = "Qwen/Qwen3.6-35B-A3B";

    private double temperature = 0.0;

    private int maxTokens = 1024;

    private int timeoutSeconds = 10;

    /** 节点未配置 aiMinConfidence 时使用 */
    private double defaultMinConfidence = 0.80;

    private String promptVersion = "v1";
}
