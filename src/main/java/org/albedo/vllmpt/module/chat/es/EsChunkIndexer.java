package org.albedo.vllmpt.module.chat.es;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识块 ES 写入器（场景4 的"倒排索引"一侧）。
 * <p>
 * <b>考点：文档 ID 与幂等</b>——当 taskId + chunkIndex 都存在时，用
 * {@code taskId:chunkIndex} 作为文档 id：
 * <ul>
 *   <li>重复写入（如消息重试、双写重试）会<b>覆盖同一文档</b>而不是产生重复 chunk；</li>
 *   <li>缺 taskId 时交给 ES 自动生成 id（旧链路/手工调用场景）。</li>
 * </ul>
 * <b>考点：Bulk API</b>——批量写入把 N 条文档打包进一次 HTTP 请求，
 * 与逐条 index 相比网络往返从 N 次降到 1 次；大文件分块入库强烈建议走 bulk。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsChunkIndexer {

    private final ElasticsearchClient esClient;

    /**
     * 单条写入（幂等：同 id 覆盖）。
     */
    public void indexChunk(KnowledgeChunkDoc doc) {
        String id = buildDocId(doc);
        try {
            IndexResponse response;
            if (id == null) {
                response = esClient.index(i -> i.index(KnowledgeChunkDoc.INDEX).document(doc));
            } else {
                response = esClient.index(i -> i.index(KnowledgeChunkDoc.INDEX).document(doc).id(id));
            }
            log.debug("[ES] 写入成功: id={}, result={}", response.id(), response.result());
        } catch (Exception e) {
            throw new BusinessException(500, "ES 写入失败: " + e.getMessage());
        }
    }

    /**
     * 批量写入（Bulk API）：整批放入一次请求；有失败项时抛异常（由上层重试/补偿）。
     */
    public void bulkIndex(List<KnowledgeChunkDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder builder = new BulkRequest.Builder();
            for (KnowledgeChunkDoc doc : docs) {
                String id = buildDocId(doc);
                if (id == null) {
                    builder.operations(op -> op.index(idx -> idx.index(KnowledgeChunkDoc.INDEX).document(doc)));
                } else {
                    builder.operations(op -> op.index(idx -> idx.index(KnowledgeChunkDoc.INDEX).document(doc).id(id)));
                }
            }
            BulkResponse response = esClient.bulk(builder.build());
            if (response.errors()) {
                // 部分失败：打印第一条错误帮助定位（常见原因：mapping 类型不匹配）
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .findFirst()
                        .ifPresent(item -> log.error("[ES] bulk 失败项: id={}, reason={}",
                                item.id(), item.error().reason()));
                throw new BusinessException(500, "ES 批量写入存在失败项，共提交 " + docs.size() + " 条");
            }
            log.debug("[ES] bulk 写入成功: {} 条", docs.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "ES 批量写入失败: " + e.getMessage());
        }
    }

    /**
     * 幂等文档 id：taskId:chunkIndex；缺任一则返回 null（由 ES 自动生成 id）。
     */
    private String buildDocId(KnowledgeChunkDoc doc) {
        if (StrUtil.isBlank(doc.getTaskId()) || doc.getChunkIndex() == null) {
            return null;
        }
        return doc.getTaskId() + ":" + doc.getChunkIndex();
    }
}
