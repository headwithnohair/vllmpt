package org.albedo.vllmpt.module.chat.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 知识块索引初始化器：启动时确保 {@link KnowledgeChunkDoc#INDEX} 存在（幂等）。
 * <p>
 * 关键设计（对照 mapping 里的类型选择）：
 * <ul>
 *   <li>{@code content} 用 <b>IK 分词</b>（索引 ik_max_word / 查询 ik_smart）——
 *       中文 BM25 检索的前提，standard 分词器把中文按单字切，相关度会明显变差；</li>
 *   <li>{@code fileName} / {@code taskId} / {@code source} 用 keyword（不分词，精确匹配/聚合）；</li>
 *   <li>单节点开发环境：1 分片 0 副本（副本在单节点上会永远是 unassigned）。</li>
 * </ul>
 * 注意：IK 插件位于 doc/docker/elasticsearch/plugins/analysis-ik，compose 中已挂载；
 * 如果日志出现 "analyzer [ik_max_word] not found"，说明 ES 容器没挂上插件——
 * 执行 {@code docker compose up -d --force-recreate elasticsearch} 重建容器即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeChunkIndexInitializer implements ApplicationRunner {

    private final ElasticsearchClient esClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(KnowledgeChunkDoc.INDEX)).value();
            if (exists) {
                log.info("[ES] 索引已存在: {}", KnowledgeChunkDoc.INDEX);
                return;
            }
            esClient.indices().create(c -> c
                    .index(KnowledgeChunkDoc.INDEX)
                    .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                    .mappings(m -> m
                            // 文本主体：IK 分词（中文）
                            .properties("content", p -> p.text(t -> t
                                    .analyzer("ik_max_word")        // 写入时：最细粒度切分，召回率高
                                    .searchAnalyzer("ik_smart")))   // 查询时：智能粗粒度，相关度高
                            // 结构化字段：keyword 不分词
                            .properties("fileName", p -> p.keyword(k -> k))
                            .properties("taskId", p -> p.keyword(k -> k))
                            .properties("chunkIndex", p -> p.integer(i -> i))
                            .properties("source", p -> p.keyword(k -> k))
                            .properties("createdAt", p -> p.date(d -> d))));
            log.info("[ES] 索引创建成功: {}（IK 分词已启用）", KnowledgeChunkDoc.INDEX);
        } catch (Exception e) {
            // 开发环境 ES 未启动/未挂 IK 插件时不让应用启动失败，只提示；
            // 首次写入或检索时会再次报错，便于定位。
            log.warn("[ES] 索引初始化失败（ES 未启动时可忽略；若报 analyzer not found 请检查 IK 插件挂载）: {}",
                    e.getMessage());
        }
    }
}
