package org.albedo.vllmpt.core.order.pipeline;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ChatPipelineContext<T> implements  PipelineContext {
    private final Map<String, Object> attributes = new HashMap<>();
    private boolean interrupted = false;
    private String interruptReason;

    public void interrupt(String reason) {
        this.interrupted = true;
        this.interruptReason = reason;
    }


    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}