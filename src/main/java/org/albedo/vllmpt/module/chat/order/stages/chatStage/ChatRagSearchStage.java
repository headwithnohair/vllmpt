package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatRagSearchStage implements ChatPipelineStage<ChatPipelineContext> {


    @Autowired
    private KnowledgeBaseRagServiceImpl knowledgeBaseRagService;

    @Override
    public void execute(ChatPipelineContext context) {
        String text = (context.getAttribute("text")).toString();
        List<Content> list= knowledgeBaseRagService.searchRelevantTexts(text,5,0.7);

        //系统信息
        String systemPrompt= buildSystemPromptWithContent(list);
        SystemMessage systemMessage =SystemMessage.from(systemPrompt);

        context.setSystemMessage(systemMessage);
    }

    private String buildSystemPromptWithContent(List<dev.langchain4j.rag.content.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return """
                你是一个智能助手，请根据你的知识回答用户问题。
                如果用户的问题超出了你的知识范围，请如实告知。
                """;
        }

        // 构建结构化的参考资料文本
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("以下是与你问题相关的参考资料，请基于这些信息回答：\n\n");

        for (int i = 0; i < contents.size(); i++) {
            dev.langchain4j.rag.content.Content content = contents.get(i);
            TextSegment segment = content.textSegment();
            String text = segment.text();
            Metadata metadata = segment.metadata();

            // 格式化每条参考信息
            contextBuilder.append("【参考资料 ").append(i + 1).append("】\n");

            // 如果有元数据（如来源），优先展示
            String source = metadata != null ? metadata.getString("source") : null;
            if (source != null && !source.isEmpty()) {
                contextBuilder.append("来源：").append(source).append("\n");
            }

            // 如果有重排分数，展示（仅用于调试，可以不展示给模型）
            // 生产环境建议不展示分数，避免模型纠结于数字
            // contextBuilder.append("相关度：").append(String.format("%.2f", score)).append("\n");

            contextBuilder.append("内容：").append(text).append("\n\n");
        }

        // 添加使用说明
        contextBuilder.append("请严格基于以上参考资料回答用户问题。");
        contextBuilder.append("如果参考资料中没有明确答案，请告诉用户未找到相关信息，不要编造。");

        return contextBuilder.toString();
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
