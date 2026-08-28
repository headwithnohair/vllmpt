package org.albedo.vllmpt.core.order.pipeline;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.Data;
import org.albedo.vllmpt.module.chat.model.entity.Attachment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ChatPipelineContext<T> implements  PipelineContext {
    private final Map<String, Object> attributes = new HashMap<>();
    private boolean interrupted = false;
    private String interruptReason;

    private ChatMemory rawChatMemory;
    private List<Attachment> attachments=new ArrayList<>();
    private UserMessage userMessage;
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