package org.albedo.vllmpt.core.order.pipeline;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
    private SystemMessage systemMessage;

    /** 附件解析后、用于写入记忆的纯文本 */
    private String memoryText;
    /** 组装好的、准备发给模型的完整消息（system + history + user） */
    private List<ChatMessage> allMessages = new ArrayList<>();
    /** 模型最终答复 */
    private AiMessage finalResult;

    /** 模型控制参数 */
    private String modelName;
    private Double temperature;
    private Integer maxTokens;

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