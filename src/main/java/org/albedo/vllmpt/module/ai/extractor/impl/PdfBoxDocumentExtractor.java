package org.albedo.vllmpt.module.ai.extractor.impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.albedo.vllmpt.module.ai.extractor.DocumentExtractor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdfBoxDocumentExtractor implements DocumentExtractor {

    /**
     * 处理超大 PDF：逐页读取 -> 切块 -> 向量化存储
     * @return 生成的摘要（用于记忆存储），以及是否触发向量化
     */
    @Override
    public String extractText(String url) {
        // 下载 PDF，使用 PDFBox 解析
        return "";
    }


    public List<TextSegment> splitDocument(Document document) {
        // chunkSize: 每个块的目标大小（字符数或token数），建议 300-500 token[reference:16]
        // chunkOverlap: 块与块之间的重叠大小，建议为 chunkSize 的 10-20%[reference:17]
        int chunkSize = 300;
        int chunkOverlap = 30;

        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(document);

        // 为每个 segment 添加元数据，比如它在原文中的位置
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            segment.metadata().put("segment_index", String.valueOf(i));
            // 可以继承 Document 的元数据，如 "source_file_name"
            segment.metadata().put("source_file_name", document.metadata().getString("source_file_name"));
        }

        return segments;
    }
}
