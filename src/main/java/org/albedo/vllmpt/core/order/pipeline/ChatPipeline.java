package org.albedo.vllmpt.core.order.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条管道 = 一组有序 Stage
 * 每条业务线构建自己的 ChatPipeline 实例
 */

public class ChatPipeline<C extends ChatPipelineContext> {

    private final String name;
    private final List<ChatPipelineStage<C>> stages;

    private ChatPipeline(String name, List<ChatPipelineStage<C>> stages) {
        this.name = name;
        this.stages = Collections.unmodifiableList(stages);
    }

    public String getName() {
        return name;
    }

    public List<ChatPipelineStage<C>> getStages() {
        return stages;
    }

    // ---------- Builder ----------
    public static <C extends ChatPipelineContext> Builder<C> builder(String name) {
        return new Builder<>(name);
    }

    public static class Builder<C extends ChatPipelineContext> {
        private final String name;
        private final List<ChatPipelineStage<C>> stages = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder<C> addStage(ChatPipelineStage<C> stage) {
            stages.add(stage);
            return this;
        }

        public ChatPipeline<C> build() {
            return new ChatPipeline<>(name, stages);
        }
    }
}