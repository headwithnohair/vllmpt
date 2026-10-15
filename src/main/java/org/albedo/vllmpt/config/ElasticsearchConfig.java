package org.albedo.vllmpt.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Elasticsearch 客户端配置（新版 Java API Client）。
 * <p>
 * 分层结构（两层包装，ES 8.x 的标准用法）：
 * <pre>
 *   ElasticsearchClient（高层 API：fluent DSL builder，本文档所有代码用它）
 *        └── ElasticsearchTransport（JSON 映射：JacksonJsonpMapper）
 *              └── RestClient（低层 HTTP：连接池 / 超时 / 认证在这里配）
 * </pre>
 * 注意：旧版 RestHighLevelClient 在 ES 8 已标记废弃，本项目不再使用。
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    /**
     * 单例 ElasticsearchClient（线程安全，全局共享）。
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties props) {
        // 1) 解析节点地址，支持 "host1:9200,host2:9200" 多节点
        HttpHost[] hosts = Arrays.stream(props.getHosts().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(h -> {
                    String[] hostPort = h.split(":");
                    int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 9200;
                    return new HttpHost(hostPort[0], port, "http");
                })
                .toArray(HttpHost[]::new);

        // 2) 构建低层 RestClient（超时 + 可选 Basic 认证，二者都在这层配置）
        var restClientBuilder = RestClient.builder(hosts)
                .setRequestConfigCallback(cfg -> cfg
                        .setConnectTimeout((int) props.getConnectionTimeout().toMillis())
                        .setSocketTimeout((int) props.getSocketTimeout().toMillis()));
        if (StringUtils.hasText(props.getUsername())) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
            restClientBuilder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(credentialsProvider));
        }
        RestClient restClient = restClientBuilder.build();

        // 3) 包装为高层客户端：RestClientTransport 负责 JSON <-> Java 对象的映射
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        log.info("[ES] ElasticsearchClient 初始化完成: hosts={}, auth={}",
                props.getHosts(), StringUtils.hasText(props.getUsername()) ? "enabled" : "disabled");
        return new ElasticsearchClient(transport);
    }
}
