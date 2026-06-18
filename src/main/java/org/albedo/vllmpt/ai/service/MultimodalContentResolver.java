package org.albedo.vllmpt.ai.service;


import dev.langchain4j.data.message.Content;
import org.albedo.vllmpt.chat.model.entity.Attachment;
import org.albedo.vllmpt.chat.model.entity.ProcessResult;
import org.albedo.vllmpt.chat.service.AttachmentProcessor;
import org.albedo.vllmpt.chat.service.impl.AttachmentProcessorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MultimodalContentResolver {

    @Autowired
    private AttachmentProcessorRegistry processorRegistry;

    /**
     * 解析附件并组装内容
     * @return 包含 模型用的 Contents 和 存入记忆用的纯文本
     */
    public ResolveResult resolve(String sessionId, String userText, List<Attachment> attachments) {
        List<Content> allContents = new ArrayList<>();
        StringBuilder memoryTextBuilder = new StringBuilder(userText);

        for (Attachment att : attachments) {
            AttachmentProcessor processor = processorRegistry.getProcessor(att.getType());
            ProcessResult result = processor.process(att, sessionId);

            allContents.addAll(result.getContentsForModel());
            if (result.getMemoryText() != null) {
                memoryTextBuilder.append(" ").append(result.getMemoryText());
            }
        }
        return new ResolveResult(allContents, memoryTextBuilder.toString());
    }

    // 内部结果封装类
    public static class ResolveResult {
        public final List<Content> contentsForModel;
        public final String memoryText;

        public ResolveResult(List<Content> contentsForModel, String memoryText) {
            this.contentsForModel = contentsForModel;
            this.memoryText = memoryText;
        }
    }
}