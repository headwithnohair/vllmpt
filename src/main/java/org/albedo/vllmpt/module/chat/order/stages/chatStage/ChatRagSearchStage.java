package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 知识库检索阶段 —— 检索相关内容并组装 SystemMessage
 * 对应 chatWithMultipleFiles 中的 searchRelevantTexts + buildSystemPromptWithContent
 */
@Slf4j
@Component
public class ChatRagSearchStage implements ChatPipelineStage<ChatPipelineContext> {

    @Autowired
    private KnowledgeBaseRagServiceImpl knowledgeBaseRagService;

    @Override
    public void execute(ChatPipelineContext context) {
        String text = context.getAttribute("text") == null ? "" : context.getAttribute("text").toString();

        List<Content> list;
        try {
            list = knowledgeBaseRagService.searchRelevantTexts(text, 5, 0.7);
            log.debug("知识库命中 {} 条参考资料", list.size());
        } catch (Exception e) {
            // 向量库/重排模型不可用（如网络异常）时降级，不能阻断整条对话链路
            log.warn("知识库检索失败，降级为通用提示词：{}", e.getMessage());
            list = List.of();
        }

        context.setSystemMessage(SystemMessage.from(buildSystemPromptWithContent(list)));
    }

    /** 没有用户文本时无需检索 */
    @Override
    public boolean shouldExecute(ChatPipelineContext context) {
        Object text = context.getAttribute("text");
        return text != null && StringUtils.hasText(text.toString());
    }

    /** 检索失败不应该阻断对话，降级为通用提示词（异常已在 execute 内兜住，此处仅作兜底日志） */
    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.warn("知识库检索失败，降级为通用提示词", e);
        context.setSystemMessage(SystemMessage.from(buildSystemPromptWithContent(List.of())));
    }

    private String buildSystemPromptWithContent(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return """
                    你是一个智能助手，请根据你的知识回答用户问题。
                    如果用户的问题超出了你的知识范围，请如实告知。
                    """;
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("以下是与你问题相关的参考资料，请基于这些信息回答：\n\n");

        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            TextSegment segment = content.textSegment();
            String text = segment.text();
            Metadata metadata = segment.metadata();

            contextBuilder.append("【参考资料 ").append(i + 1).append("】\n");

            String source = metadata != null ? metadata.getString("source") : null;
            if (StringUtils.hasText(source)) {
                contextBuilder.append("来源：").append(source).append("\n");
            }

            contextBuilder.append("内容：").append(text).append("\n\n");
        }

        contextBuilder.append("请严格基于以上参考资料回答用户问题。");
        contextBuilder.append("如果参考资料中没有明确答案，请告诉用户未找到相关信息，不要编造。");

        return contextBuilder.toString();
    }
}
