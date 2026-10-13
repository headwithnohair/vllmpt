package org.albedo.vllmpt.module.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大文件解析任务配置，对应 application-dev.yml 中的 app.file-task 段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.file-task")
public class FileTaskProperties {

    /** 进度 Hash 的过期时间（秒）：兜底清理，防止异常中断的任务 key 永久残留 */
    private long progressTtlSeconds = 86400;

    /** 单阶段超时阈值（秒）：超过该时长仍未完成的任务由延迟检查消息判定超时 */
    private long stageTimeoutSeconds = 1800;
}
