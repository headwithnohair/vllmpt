package org.albedo.vllmpt.module.chat.service.impl;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.ChatStream;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class DefaultChatStream implements ChatStream {

    private final StreamingChatModel model;
    private final List<ChatMessage> messages;
    private final ChatMemory memory;
    private final String userMemoryText; // 用于存入 memory 的纯文本摘要

    // 默认的回调处理器（防止调用方不设置时报空指针）
    private Consumer<String> onNextHandler = token -> {};
    private Consumer<Response<AiMessage>> onCompleteHandler = response -> {};
    private Consumer<Throwable> onErrorHandler = error -> {};

    public DefaultChatStream(StreamingChatModel model, List<ChatMessage> messages,
                             ChatMemory memory, String userMemoryText) {
        this.model = model;
        this.messages = messages;
        this.memory = memory;
        this.userMemoryText = userMemoryText;
    }

    @Override
    public ChatStream onNext(Consumer<String> handler) {
        this.onNextHandler = handler;
        return this;
    }

    @Override
    public ChatStream onComplete(Consumer<Response<AiMessage>> handler) {
        this.onCompleteHandler = handler;
        return this;
    }

    @Override
    public ChatStream onError(Consumer<Throwable> handler) {
        this.onErrorHandler = handler;
        return this;
    }

    @Override
    public void start() {
        // 构建 LangChain4j 原生的 Handler
        StreamingChatResponseHandler lcHandler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                onNextHandler.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                try {
                    // 从 ChatResponse 中获取 AiMessage
                    AiMessage aiMessage = response.aiMessage();

                    // 更新 Memory
                    memory.add(UserMessage.from(userMemoryText));
                    memory.add(aiMessage);

                    // 触发调用方的 onComplete 回调
                    onCompleteHandler.accept(Response.from(aiMessage));
                } catch (Exception e) {
                    onErrorHandler.accept(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                onErrorHandler.accept(error);
            }
        };

        // 发起流式调用
        model.chat(messages, lcHandler);
    }
}
