package org.albedo.vllmpt.module.order.stages;


import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class test2Stage implements ChatPipelineStage<ChatPipelineContext> {


    @Override
    public void execute(ChatPipelineContext context) {

        log.info("执行 execute");
    }

    @Override
    public String name() {

        return ChatPipelineStage.super.name();
    }

    @Override
    public boolean shouldExecute(ChatPipelineContext context) {
        boolean shouldExecute = true;

        log.info("执行 shouldExecute 返回 {}",shouldExecute);
        return shouldExecute;
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        ChatPipelineStage.super.onError(context, e);
    }
}
