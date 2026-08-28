package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;

public class ChatLoadInfoStage implements ChatPipelineStage<ChatPipelineContext> {
    @Override
    public void execute(ChatPipelineContext context) {

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
