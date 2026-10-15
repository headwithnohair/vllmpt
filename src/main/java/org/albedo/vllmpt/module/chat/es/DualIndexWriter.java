package org.albedo.vllmpt.module.chat.es;

import cn.hutool.core.convert.Convert;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 双写门面（场景4 需求1）：同一份分块同时写入两个索引——
 * <pre>
 *   Chroma（向量）：语义召回       —— knowledgeBaseRagService.indexTextSegment
 *   ES   （倒排）：BM25 关键词召回  —— esChunkIndexer.indexChunk / bulkIndex
 * </pre>
 * <b>接线说明（留给你决定）</b>：本类目前无人调用。把现有入库链路的调用点切过来即可——
 * <ul>
 *   <li>方案 A（推荐）：场景6 的 {@code EmbedConsumer} 里，把
 *       {@code knowledgeBaseRagService.indexTextSegment(segment)} 换成
 *       {@code dualIndexWriter.indexSegment(segment)}（记得给 metadata 打上 taskId，
 *       ES 侧就能获得幂等文档 id）；</li>
 *   <li>方案 B：改造 {@code TextEmbedding.chunkEmbeding}（影响旧同步链路）。</li>
 * </ul>
 * <b>一致性提示</b>：这里的策略是"先 Chroma 后 ES，任一失败直接抛异常"——
 * 与场景6 的失败哲学一致：让 MQ 重试机制兜底（ES 侧幂等 id 天然免疫重复写入）。
 * 若要求更严格（如两库最终一致），可考虑：失败补偿重投 / 定时对账（chroma id ↔ ES id）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualIndexWriter {

    private final KnowledgeBaseRagServiceImpl knowledgeBaseRagService;
    private final EsChunkIndexer esChunkIndexer;

    /**
     * 双写单个分块。
     */
    public void indexSegment(TextSegment segment) {
        // 1) Chroma：向量化 + 入库
        knowledgeBaseRagService.indexTextSegment(segment);
        // 2) ES：倒排索引（BM25 检索用）
        esChunkIndexer.indexChunk(toDoc(segment));
    }

    /**
     * 双写一批分块（ES 侧走 Bulk，大文件推荐）。
     */
    public void indexSegments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (TextSegment segment : segments) {
            knowledgeBaseRagService.indexTextSegment(segment);
        }
        esChunkIndexer.bulkIndex(segments.stream().map(this::toDoc).toList());
        log.info("[双写] 完成 {} 个分块 → Chroma + ES", segments.size());
    }

    /**
     * TextSegment（langchain4j）→ KnowledgeChunkDoc（ES 文档）：
     * 从 metadata 提取结构化字段，提取等值于双写时"同一份元数据走两个库"。
     */
    private KnowledgeChunkDoc toDoc(TextSegment segment) {
        Metadata metadata = segment.metadata();
        return KnowledgeChunkDoc.builder()
                .content(segment.text())
                .fileName(getString(metadata, "fileName"))
                .taskId(getString(metadata, "taskId"))
                .chunkIndex(getChunkIndex(metadata))
                .source(getString(metadata, "source"))
                .createdAt(System.currentTimeMillis())
                .build();
    }

    private String getString(Metadata metadata, String key) {
        if (metadata == null) {
            return null;
        }
        return Convert.toStr(metadata.getString(key), null);
    }

    private Integer getChunkIndex(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        return Convert.toInt(metadata.getString("chunkIndex"), null);
    }
}
