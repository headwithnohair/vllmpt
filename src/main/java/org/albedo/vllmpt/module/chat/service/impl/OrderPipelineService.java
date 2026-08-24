package org.albedo.vllmpt.module.chat.service.impl;

import org.albedo.vllmpt.core.order.pipeline.ChatPipeline;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.module.chat.order.stages.Test2Stage;
import org.albedo.vllmpt.module.chat.order.stages.TestStage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderPipelineService {

    @Bean("chatPipeline")
    public ChatPipeline<ChatPipelineContext> chatPipeline(
            TestStage testStage,
            Test2Stage test2Stage

    ) {
        return ChatPipeline.<ChatPipelineContext>builder("chat-main")
                .addStage(testStage)      // 参数校验
                .addStage(test2Stage)
                .build();
    }
}

