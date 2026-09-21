package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineStage;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.albedo.vllmpt.module.chat.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * 模型调用阶段 —— 带工具调用的多轮 Agent 循环
 * 对应 chatWithMultipleFiles 中 chatModelFactory.createModel + while(stepCount < 10) 的循环
 * <p>
 * 若上下文注入了 {@link ChatPipelineContext#STREAM_TOKEN_CONSUMER}，则改用
 * {@link StreamingChatModel} 逐 token 推送；否则与原来一样整段同步返回。
 */
@Slf4j
@Component
public class ChatAgentStage implements ChatPipelineStage<ChatPipelineContext> {

    /** 最大工具调用轮次，防止死循环 */
    private static final int MAX_STEPS = 10;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private ToolRegistry toolRegistry;

    @Override
    public void execute(ChatPipelineContext context) {

        List<ChatMessage> sourceMessages = context.getAllMessages();
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            context.interrupt("待发送给模型的消息为空");
            return;
        }

        // 存在流式通道 => 走 SSE 逐 token 推送；否则走同步整段返回
        Consumer<String> tokenConsumer = (Consumer<String>) context.getAttribute(ChatPipelineContext.STREAM_TOKEN_CONSUMER);

        ChatModel chatModel = null;
        StreamingChatModel streamingModel = null;
        if (tokenConsumer == null) {
            chatModel = chatModelFactory.createModel(
                    context.getModelName(), context.getTemperature(), context.getMaxTokens());
        } else {
            streamingModel = chatModelFactory.createStreamingModel(
                    context.getModelName(), context.getTemperature(), context.getMaxTokens());
        }

        List<ToolSpecification> specs = toolRegistry.allSpecs();

        List<ChatMessage> messages = new ArrayList<>(sourceMessages);
        AiMessage finalResult = null;

        for (int step = 0; step < MAX_STEPS; step++) {
            log.info("第 {} 轮模型调用{}", step + 1, streamingModel != null ? "（流式）" : "");

            ChatRequest chatRequest = ChatRequest.builder()
                    .toolSpecifications(specs)
                    .messages(messages)
                    .build();

            AiMessage aiMessage = streamingModel != null
                    ? chatOnceStreaming(streamingModel, chatRequest, tokenConsumer)
                    : chatModel.chat(chatRequest).aiMessage();

            // 没有工具调用请求 => 已是最终答复
            if (!aiMessage.hasToolExecutionRequests()) {
                finalResult = aiMessage;
                break;
            }

            log.info("模型请求执行工具：{}", aiMessage.toolExecutionRequests());

            // 先把 AI 的工具调用意图放回上下文，再逐个执行并回填结果
            messages.add(aiMessage);
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                String result = toolRegistry.executeSafely(request);
                messages.add(ToolExecutionResultMessage.from(request, result));
            }

            // TODO 上下文过长时在此做摘要
        }

        if (finalResult == null) {
            context.interrupt("模型在 " + MAX_STEPS + " 轮工具调用后仍未给出最终答复");
            return;
        }

        context.setFinalResult(finalResult);
    }

    /**
     * 发起一次流式调用：逐 token 回调 tokenConsumer，并阻塞等待本轮完整结果。
     * 工具调用轮次通常不会产生正文 token，因此不会污染最终输出。
     */
    private AiMessage chatOnceStreaming(StreamingChatModel model,
                                        ChatRequest request,
                                        Consumer<String> tokenConsumer) {
        CompletableFuture<AiMessage> future = new CompletableFuture<>();

        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                tokenConsumer.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                future.complete(response.aiMessage());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式调用被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("流式调用失败", e.getCause());
        }
    }

    @Override
    public void onError(ChatPipelineContext context, Throwable e) {
        log.error("模型调用失败", e);
        context.interrupt("模型调用失败：" + e.getMessage());
    }
}
