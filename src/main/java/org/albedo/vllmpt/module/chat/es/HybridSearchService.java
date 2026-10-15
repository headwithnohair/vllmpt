package org.albedo.vllmpt.module.chat.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 混合检索服务（场景4 核心）：BM25 关键词路 + 向量语义路 → RRF 融合。
 * <p>
 * 需求映射：
 * <ul>
 *   <li>需求1（双写双索引）→ {@link DualIndexWriter}；</li>
 *   <li>需求2（查询时同时发起向量检索 + ES 全文检索）→ {@link #hybridSearch}；</li>
 *   <li>需求3（RRF 融合返回 Top-K）→ {@link #rrfFuse}。</li>
 * </ul>
 * 为什么需要混合：BM25 对专有名词/编号/精确词命中强但不懂语义；
 * 向量检索懂语义但容易漏掉"字面完全匹配"的词 —— 两路互补，RRF 把两路排名合并。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    /**
     * RRF 平滑常数 k（经典值 60）：
     * score = 1/(k+rank)，k 越大，各名次之间的分数差越平缓；
     * 它的作用只是"压平"排名衰减曲线，取值不敏感（文献常用 60）。
     */
    private static final int RRF_K = 60;

    private final ElasticsearchClient esClient;
    private final KnowledgeBaseRagServiceImpl knowledgeBaseRagService;

    /**
     * 混合检索入口：两路各取 {@code topK * 2} 个候选，RRF 融合后返回 Top-K 分块文本。
     * <p>
     * 候选池比最终结果大一倍：给融合环节更多"票源"，
     * 两路各自靠前的文档更容易在融合中叠加得分。
     */
    public List<String> hybridSearch(String query, int topK) {
        int candidateSize = topK * 2;

        // 路 1：ES BM25 关键词检索（倒排索引）
        List<String> bm25Hits = bm25Search(query, candidateSize);

        // 路 2：Chroma 向量语义检索（走现成链路，minScore=0 不设阈值放开召回）
        List<String> vectorHits = knowledgeBaseRagService.searchRelevantTexts(query, candidateSize, 0.0).stream()
                .map(Content::textSegment)
                .filter(Objects::nonNull)
                .map(TextSegment::text)
                .toList();

        log.info("[混合检索] query='{}' → BM25 {} 条 / 向量 {} 条，RRF 取 Top-{}",
                query, bm25Hits.size(), vectorHits.size(), topK);

        // 路 3：RRF 融合两路排名
        return rrfFuse(List.of(bm25Hits, vectorHits), topK);
    }

    /**
     * BM25 全文检索（路 1）——TODO(1)：你来补全（考点：ES DSL / multi_match / 进阶 function_score）。
     * <p>
     * 目标：用查询词检索 {@link KnowledgeChunkDoc#INDEX}，按 BM25 相关度降序返回
     * content 文本列表（长度 ≤ size）。
     * <p>
     * 可抄骨架（补全时去掉注释符；需 import
     * {@code co.elastic.clients.elasticsearch.core.SearchResponse} 和 {@code java.io.IOException}）：
     * <pre>{@code
     * try {
     *     SearchResponse<KnowledgeChunkDoc> response = esClient.search(s -> s
     *             .index(KnowledgeChunkDoc.INDEX)
     *             .query(q -> q
     *                     .multiMatch(m -> m
     *                             .query(keyword)
     *                             .fields("content")))   // content 已配 IK 分词（写 ik_max_word / 查 ik_smart）
     *             .size(size),
     *             KnowledgeChunkDoc.class);
     *     return response.hits().hits().stream()
     *             .map(hit -> hit.source().getContent())
     *             .toList();
     * } catch (IOException e) {
     *     // 降级策略：BM25 路失败不拖垮整个混合检索，返回空列表由向量路兜底
     *     log.error("[混合检索] BM25 查询失败，降级为空结果", e);
     *     return List.of();
     * }
     * }</pre>
     * 进阶玩法（自选，对应 todo 里的考点）：
     * <ul>
     *   <li>{@code bool.should} 组合多字段：content 全文匹配 + fileName 精确匹配；</li>
     *   <li>{@code function_score} 叠加业务因子：如 createdAt 越新得分越高（时间衰减）；</li>
     *   <li>建议先在 Kibana Dev Tools 用 DSL 验证命中效果，再翻译成 fluent Java API。</li>
     * </ul>
     */
    private List<String> bm25Search(String keyword, int size) {
        // TODO(1): 按上方骨架实现 ES BM25 检索
        throw new UnsupportedOperationException("TODO(1): 实现 bm25Search —— 参考上方 javadoc 骨架");
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合（路 3）——TODO(2)：你来补全。
     * <p>
     * 公式：{@code score(doc) = Σ_每条榜单 1 / (RRF_K + rank)}，rank 从 <b>1</b> 开始。
     * 关键特性：<b>只用名次、不用原始分数</b> —— 天然规避 BM25 分数与余弦相似度的量纲差异，
     * 两路结果不需要做任何归一化就能直接合并。
     * <p>
     * 实现步骤（伪代码，可照抄）：
     * <pre>{@code
     * Map<String, Double> scoreMap = new HashMap<>();
     * for (List<String> ranked : rankedLists) {
     *     for (int i = 0; i < ranked.size(); i++) {
     *         scoreMap.merge(ranked.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
     *     }
     * }
     * return scoreMap.entrySet().stream()
     *         .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
     *         .limit(topK)
     *         .map(Map.Entry::getKey)
     *         .toList();
     * }</pre>
     * 效果直觉：两路都进 Top 榜的 chunk 被加两次分，会被顶到最前；
     * 只在单路霸榜的文档也有机会排前（各贡献一份票）。
     * <p>
     * 融合键约定：两路元素都是"分块原文"，同一条 chunk 在两路中文本相同即视为同一文档。
     * 生产上更稳的 key 是 {@code taskId:chunkIndex}（前提是两路都带结构化元数据）。
     * <p>
     * 验证思路：选一个含专有名词的查询（项目名/版本号），BM25 路应命中、向量路可能漏，
     * 融合结果里该条应比纯向量检索更靠前。
     */
    private List<String> rrfFuse(List<List<String>> rankedLists, int topK) {
        // TODO(2): 按上方伪代码实现 RRF 融合
        throw new UnsupportedOperationException("TODO(2): 实现 rrfFuse —— 参考上方 javadoc 伪代码");
    }
}
