package org.albedo.vllmpt.ai.strategy;

import dev.langchain4j.data.message.TextContent;
import org.albedo.vllmpt.ai.extractor.DocumentExtractor;
import org.albedo.vllmpt.chat.model.entity.Attachment;
import org.albedo.vllmpt.chat.model.entity.ProcessResult;
import org.albedo.vllmpt.chat.service.AttachmentProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class DocumentProcessor implements AttachmentProcessor {
    @Autowired
    private DocumentExtractor extractor;
//    @Autowired private VectorStore vectorStore;
//    @Autowired private TextSplitter splitter;

    private static final int SHORT_TEXT_THRESHOLD = 500;

    @Override
    public boolean supports(String type) {
        return Set.of("pdf", "doc", "txt", "md").contains(type);
    }

    @Override
    public ProcessResult process(Attachment attachment, String sessionId) {
        String fullText = null; // 下载并解析
        try {
            fullText = extractor.extractText(attachment.getUrl());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int len = fullText.length();

        if (len <= SHORT_TEXT_THRESHOLD) {
            // 短内容：直接作为文本附上
            return new ProcessResult(
                    List.of(TextContent.from(fullText)),
                    "[文档内容: " + fullText + "]",
                    false, null
            );
        } else {
            // 长内容：生成摘要，并触发异步向量化
            String summary = generateSummary(fullText); // 可调用小模型或截取前几行
//            List<TextSegment> segments = splitter.split(fullText, 500);
//            // 异步存储到向量库（或同步，视需求）
//            vectorStore.add(segments, sessionId, attachment.getName());
            return new ProcessResult(
                    List.of(TextContent.from("【文档摘要】" + summary)),
                    "[长文档: " + attachment.getName() + " 已索引]",
                    true, fullText  // 标记需要向量化，实际已做
            );
        }
    }

    private String generateSummary(String text) {
        // 简单实现：取前200字 + …
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}