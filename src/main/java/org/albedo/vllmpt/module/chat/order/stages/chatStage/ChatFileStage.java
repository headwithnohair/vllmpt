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

@Slf4j
@Component
public class ChatFileStage implements ChatPipelineStage<ChatPipelineContext> {

    @Autowired
    private MultimodalContentResolver contentResolver;


    @Override
    public void execute(ChatPipelineContext context) {

        String sessionId =context.getAttribute("sessionId").toString();
        List<Attachment> rqs =context.getAttachments();
        String text = (context.getAttribute("text")).toString();
        List<Content> currentContents = new ArrayList<>();


        MultimodalContentResolver.ResolveResult resolveResult = contentResolver.resolve(sessionId, text,rqs);

        //将记忆用的图片imageMessage替换为文字标签 放入list ,解析后的文件 也放入列表,对于模型不支持的文件,转换为文字Message后放入list
        currentContents.add(TextContent.from(resolveResult.memoryText));
        currentContents.addAll(resolveResult.contentsForModel);
        UserMessage currentUserMsg = UserMessage.from(currentContents);
        context.setUserMessage(currentUserMsg);
    }

    @Override
    public String name() {
        return ChatPipelineStage.super.name();
    }

    @Override
    public boolean shouldExecute(ChatPipelineContext context) {
        return ChatPipelineStage.super.shouldExecute(context);
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        ChatPipelineStage.super.onError(context, e);
    }
}
