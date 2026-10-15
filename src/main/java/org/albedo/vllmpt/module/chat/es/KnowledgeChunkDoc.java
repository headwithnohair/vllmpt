package org.albedo.vllmpt.module.chat.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库分块的 ES 文档模型（对应索引 vllmpt_knowledge_chunks 的 mapping）。
 * <p>
 * 字段设计与 Chroma 侧的 Metadata 保持同源（content 即 TextSegment.text()）：
 * <pre>
 *   content    text    —— 全文检索主体（IK 分词，BM25 算分）
 *   fileName   keyword —— 精确过滤/聚合用；keyword 不分词
 *   taskId     keyword —— 业务主键（与场景6 台账、Chroma metadata 对齐）
 *   chunkIndex integer —— 块序号
 *   source     keyword —— 来源标识（如 MinIO 对象名）
 *   createdAt  date    —— epoch millis
 * </pre>
 * 为什么 text 和 keyword 要区分：text 会分词、用于 match/multi_match 检索；
 * keyword 不分词，用于 term 精确匹配、排序、聚合——选错类型是 ES 最常见的坑。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkDoc {

    /** ES 索引名（本场景的倒排索引） */
    public static final String INDEX = "vllmpt_knowledge_chunks";

    /** 分块文本（BM25 检索主体） */
    private String content;

    /** 原始文件名（keyword，精确匹配/过滤用） */
    private String fileName;

    /** 任务 ID（场景6 台账主键；双写时带上可获得幂等 id） */
    private String taskId;

    /** 块序号（配合 taskId 组成幂等文档 id） */
    private Integer chunkIndex;

    /** 来源（建议放 MinIO 对象名或业务来源标识） */
    private String source;

    /** 写入时间（epoch millis，ES date 类型原生支持） */
    private Long createdAt;
}
