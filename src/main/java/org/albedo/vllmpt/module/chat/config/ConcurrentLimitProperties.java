package org.albedo.vllmpt.module.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 单用户并发推理限制配置，对应 application-dev.yml 中的 ai.concurrent 段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.concurrent")
public class ConcurrentLimitProperties {

    /** 单用户允许的最大并发推理任务数 */
    private int limit = 3;

    /** 活跃窗口（秒）：score 早于 now-window 的 member 视为僵尸，自动清理 */
    private int windowSeconds = 300;

    /** key 兜底过期时间（秒），应大于 windowSeconds */
    private int keyTtlSeconds = 600;
}
