package org.albedo.vllmpt.module.chat.handler.chatFilesProcessor;

import dev.langchain4j.data.message.TextContent;
import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.ProcessResult;
import org.albedo.vllmpt.module.chat.service.AttachmentProcessor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoProcessor implements AttachmentProcessor {
//    @Autowired
//    private VideoAnalyzer videoAnalyzer; // 调用多模态模型抽帧描述

    @Override
    public boolean supports(String type) {
        return "video".equals(type);
    }

    @Override
    public ProcessResult process(Attachment attachment, String sessionId) {
        // 调用远程服务生成视频描述（或转文字）
//        String description = videoAnalyzer.describe(attachment.getUrl());
        String description = "1231qadsad";
        // 描述一般较短，直接作为文本传给模型
        return new ProcessResult(
                List.of(TextContent.from("[视频内容] " + description)),
                "[视频: " + attachment.getName() + " 内容: " + description + "]",
                false, null
        );
    }
}
