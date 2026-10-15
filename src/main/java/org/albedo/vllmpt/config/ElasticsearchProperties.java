package org.albedo.vllmpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Elasticsearch 配置绑定，对应 application-dev.yml 中的 elasticsearch 段。
 * <p>
 * 版本约定：客户端（co.elastic.clients:elasticsearch-java）必须与 docker 中的
 * 服务端（elasticsearch:8.11.0）保持同一 minor 版本，否则请求会被拒绝。
 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    /** 节点地址，支持逗号分隔多节点（如 "host1:9200,host2:9200"） */
    private String hosts = "localhost:9200";

    /** 用户名（compose 已关闭 security，留空即可；生产开启认证后填写） */
    private String username;

    /** 密码 */
    private String password;

    /** 建立连接的超时时间 */
    private Duration connectionTimeout = Duration.ofSeconds(5);

    /** 读写（socket）超时时间 */
    private Duration socketTimeout = Duration.ofSeconds(30);
}
