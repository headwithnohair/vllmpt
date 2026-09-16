package org.albedo.vllmpt.module.order.config;

import org.albedo.vllmpt.core.order.pipeline.ChatPipeline;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.module.chat.order.stages.chatStage.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatPipelineConfig {

    @Bean("chatPipeline")
    public ChatPipeline<ChatPipelineContext> chatPipeline(
            ChatMemoryStage chatMemoryStage,
            ChatRagSearchStage chatRagSearchStage,
            ChatFileStage chatFileStage,
            ChatLoadInfoStage chatLoadInfoStage,
            ChatAgentStage chatAgentStage,
            ChatMemorySaveStage chatMemorySaveStage
    ) {
        return ChatPipeline.<ChatPipelineContext>builder("chat-main")
                .addStage(chatMemoryStage)      // 加载会话记忆
                .addStage(chatRagSearchStage)   // 知识库检索 -> system 提示词
                .addStage(chatFileStage)        // 附件解析 -> 多模态 user 消息
                .addStage(chatLoadInfoStage)    // 组装上下文 + 模型参数
                .addStage(chatAgentStage)       // 模型调用（含工具循环）
                .addStage(chatMemorySaveStage)  // 记忆落库 + 输出响应
                .build();
    }
}
