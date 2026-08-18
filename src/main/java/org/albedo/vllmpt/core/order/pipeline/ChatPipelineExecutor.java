package org.albedo.vllmpt.core.order.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatPipelineExecutor {

    /**
     * 执行一条完整的 Pipeline
     */
    public <C extends ChatPipelineContext> void execute(ChatPipeline<C> pipeline, C context) {

        log.info("Pipeline [{}] start, stages={}", pipeline.getName(), pipeline.getStages().size());

        for (ChatPipelineStage<C> stage : pipeline.getStages()) {

            // 1. 中断检查
            if (context.isInterrupted()) {
                log.warn("Pipeline [{}] interrupted before [{}]: {}",
                        pipeline.getName(), stage.name(), context.getInterruptReason());
                break;
            }

            // 2. 条件跳过
            if (!stage.shouldExecute(context)) {
                log.info("Pipeline [{}] skip stage [{}]", pipeline.getName(), stage.name());
                continue;
            }

            // 3. 执行
            try {
                long start = System.currentTimeMillis();
                stage.execute(context);
                log.info("Pipeline [{}] stage [{}] done in {}ms",
                        pipeline.getName(), stage.name(), System.currentTimeMillis() - start);

            } catch (Exception e) {
                log.error("Pipeline [{}] stage [{}] error", pipeline.getName(), stage.name(), e);
                handleException(pipeline, stage, context, e);
                break;
            }
        }

        log.info("Pipeline [{}] finished, interrupted={}", pipeline.getName(), context.isInterrupted());
    }

    /**
     * 异常处理：调用 stage 的 onError，可在此扩展告警、补偿等
     */
    private <C extends ChatPipelineContext> void handleException(
            ChatPipeline<C> pipeline,
            ChatPipelineStage<C> stage,
            C context,
            Exception e) {
        try {
            stage.onError(context, e);
        } catch (Exception ex) {
            log.error("Pipeline [{}] stage [{}] onError also failed", pipeline.getName(), stage.name(), ex);
        }
    }
}