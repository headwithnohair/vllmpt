package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.ai.service.MultimodalContentResolver;
import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 附件解析阶段 —— 解析多模态附件，生成模型用的 UserMessage 与写入记忆用的纯文本
 * 对应 chatWithMultipleFiles 中的 contentResolver.resolve(...)
 */
@Slf4j
@Component
public class ChatFileStage implements ChatPipelineStage<ChatPipelineContext> {

    @Autowired
    private MultimodalContentResolver contentResolver;

    @Override
    public void execute(ChatPipelineContext context) {

        Object sessionIdObj = context.getAttribute("sessionId");
        String sessionId = sessionIdObj == null ? null : sessionIdObj.toString();

        Object textObj = context.getAttribute("text");
        String text = textObj == null ? "" : textObj.toString();

        List<Attachment> attachments = context.getAttachments();
        if (attachments == null) {
            attachments = List.of();
        }

        MultimodalContentResolver.ResolveResult resolveResult =
                contentResolver.resolve(sessionId, text, attachments);

        // 记忆用的纯文本（图片/文件会被替换为文字标签）
        context.setMemoryText(resolveResult.memoryText);

        // 模型用的多模态内容
        List<Content> currentContents = new ArrayList<>();
        currentContents.add(TextContent.from(resolveResult.memoryText));
        currentContents.addAll(resolveResult.contentsForModel);

        context.setUserMessage(UserMessage.from(currentContents));
        log.debug("附件解析完成，模型内容 {} 段", currentContents.size());
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.error("附件解析失败", e);
        context.interrupt("附件解析失败：" + e.getMessage());
    }
}
