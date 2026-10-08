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

    /**
     * 流式输出通道的 attribute key。
     * 值为 {@code java.util.function.Consumer<String>}，存在时表示本次走 SSE 流式，
     * Stage 应逐 token 推送内容而非整段返回。
     */
    public static final String STREAM_TOKEN_CONSUMER = "streamTokenConsumer";

    /** 本次推理请求唯一 ID 的 attribute key，用于释放并发额度（ZSet 的 member） */
    public static final String REQUEST_ID = "requestId";

    /**
     * 本次推理真实消耗 Token 的 attribute key。
     * <p>
     * 值为 {@code dev.langchain4j.model.output.TokenUsage}，由 {@code ChatAgentStage} 逐轮累加后写入，
     * 供接入层在结算时读取。可能为 {@code null}（流式响应通常不带用量）。
     */
    public static final String TOKEN_USAGE = "tokenUsage";

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

    /** 本次推理请求的唯一 ID，用于释放并发额度（ZSet 的 member） */
    private String requestId;

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