package org.albedo.vllmpt.ai.strategy;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.chat.model.entity.Attachment;
import org.albedo.vllmpt.chat.model.entity.ProcessResult;
import org.albedo.vllmpt.chat.service.AttachmentProcessor;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ImageProcessor implements AttachmentProcessor {
    @Override
    public boolean supports(String type) {
        return "image".equals(type);
    }

    @Override
    public ProcessResult process(Attachment attachment, String sessionId) {
        // 直接使用 URL 作为 ImageContent
        List<Content> contents = List.of(ImageContent.from(attachment.getUrl()));
        //后续考虑面向非多模态模型时,添加图片的语义描述,.
        // 记忆只存标记，不存 URL
        String memoryText = "[图片: " + attachment.getName() + "]";
        return new ProcessResult(contents, memoryText, false, null);
    }
}
