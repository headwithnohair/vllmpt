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

    /** 灰度比例 0-100，按实例 ID 稳定分桶；100 为全量 */
    private int rolloutPercent = 100;

    private String modelName = "Qwen/Qwen3.6-35B-A3B";

    private double temperature = 0.0;

    private int maxTokens = 1024;

    private int timeoutSeconds = 10;

    /** 节点未配置 aiMinConfidence 时使用 */
    private double defaultMinConfidence = 0.80;

    private String promptVersion = "v1";

    public boolean allows(Long instanceId) {
        if (rolloutPercent >= 100) {
            return true;
        }
        if (rolloutPercent <= 0) {
            return false;
        }
        long bucket = Math.floorMod(instanceId == null ? 0L : instanceId, 100L);
        return bucket < rolloutPercent;
    }
}
