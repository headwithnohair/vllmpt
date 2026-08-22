package org.albedo.vllmpt.module.chat.controller;


import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.core.order.pipeline.ChatPipeline;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineExecutor;
import org.albedo.vllmpt.module.chat.model.entity.ModelPipelineContext;
import org.albedo.vllmpt.module.chat.service.impl.OrderPipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/pipline")
public class ChatPiplineController {

    @Autowired @Qualifier("chatPipeline")
    private ChatPipeline<ChatPipelineContext> chatPipeline;

    @Autowired
    private ChatPipelineExecutor executor;

    @PostMapping("/fack")
    public Result<String> testPipline(ModelPipelineContext mpc){

        ChatPipelineContext ctx =new ChatPipelineContext();
        ctx.setAttribute("request", mpc);
        ctx.setAttribute("modelId", mpc.getModelId());
        ctx.setAttribute("sessionId", mpc.getSessionId());
        executor.execute(chatPipeline, ctx);
        if (ctx.isInterrupted()) {
            return Result.error(505,ctx.getInterruptReason());
        }
        return  Result.success(ctx.getAttribute("response").toString());
    }
}
