package org.albedo.vllmpt.common.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警配置，对应 application-dev.yml 中的 app.alert 段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.alert")
public class AlertProperties {

    /** 是否启用 webhook 推送；false=只打本地 ERROR 日志 */
    private boolean enabled = true;

    /** 钉钉/飞书机器人 webhook 地址；留空=只打本地日志 */
    private String webhookUrl = "";

    /** webhook 消息格式：DINGTALK / FEISHU */
    private String format = "DINGTALK";
}
