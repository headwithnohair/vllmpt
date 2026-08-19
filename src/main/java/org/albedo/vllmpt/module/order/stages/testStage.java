package org.albedo.vllmpt.module.order.stages;


import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.springframework.stereotype.Component;

@Component
public class testStage implements ChatPipelineStage<ChatPipelineContext> {


    @Override
    public void execute(ChatPipelineContext context) {

    }
}
