package org.albedo.vllmpt.module.ai.extractor.impl;

import org.albedo.vllmpt.module.ai.extractor.DocumentExtractor;
import org.springframework.stereotype.Component;

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
}
