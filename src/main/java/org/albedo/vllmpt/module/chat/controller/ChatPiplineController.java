package org.albedo.vllmpt.module.chat.controller;


import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.core.order.pipeline.ChatPipeline;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineExecutor;
import org.albedo.vllmpt.module.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.module.chat.service.ChatSessionLockService;
import org.albedo.vllmpt.module.chat.service.ConcurrentLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


@RestController
@RequestMapping("/api/pipline")
public class ChatPiplineController {

    /** 流结束标记，前端收到后可关闭连接 */
    private static final String DONE = "[DONE]";

    /** 并发额度不足的业务错误码 */
    private static final int CODE_TOO_MANY_CONCURRENT = 429;

    /** 会话正在处理中（会话锁被占用）的业务错误码 */
    private static final int CODE_SESSION_BUSY = 409;

    @Autowired @Qualifier("chatPipeline")
    private ChatPipeline<ChatPipelineContext> chatPipeline;

    @Autowired
    private ChatPipelineExecutor executor;

    @Autowired
    private ConcurrentLimitService concurrentLimitService;

    @Autowired
    private ChatSessionLockService sessionLockService;

    @PostMapping("/fack")
    public Result<String> testPipline(@RequestBody(required = false) MultimodalChatRequest mpc){

        ChatPipelineContext ctx = buildContext(mpc);
        if (ctx.isInterrupted()) {
            return Result.error(505, ctx.getInterruptReason());
        }

        String userId = resolveUserId(ctx);
        String requestId = UUID.randomUUID().toString();
        ctx.setAttribute(ChatPipelineContext.REQUEST_ID, requestId);

        // 会话锁用的原始 sessionId：不能用 resolveUserId 的 "anonymous" 兜底，
        // 否则所有匿名请求会挤在同一个锁上互相阻塞（为空时 Service 内部会直接放行）
        String sessionId = (String) ctx.getAttribute("sessionId");

        boolean res = concurrentLimitService.tryAcquire(userId,requestId);
        if ( !res)
            throw new BusinessException(CODE_TOO_MANY_CONCURRENT ,"您的并发请求过多，请稍后重试") ;

        boolean sessionLocked = false;
        try {

            sessionLocked=sessionLockService.tryLockSession(sessionId);
            if (!sessionLocked){
                throw new BusinessException(CODE_SESSION_BUSY, "该会话正在处理中，请稍候再试");
            }
            executor.execute(chatPipeline, ctx);
            if (ctx.isInterrupted()) {
                return Result.error(505, ctx.getInterruptReason());
            }
            Object response = ctx.getAttribute("response");
            if (response == null) {
                return Result.error(505, "流水线未产生响应");
            }
            return Result.success(response.toString());
        } finally {

            if (sessionLocked) { sessionLockService.unlockSession(sessionId); }
            concurrentLimitService.release(userId,requestId);
        }
    }

    /**
     * 与 /fack 等价的 SSE 流式接口：模型输出逐 token 推送，结束时推送 [DONE]。
     * <p>
     * 注意：Pipeline 本身是同步阻塞的，这里放到独立线程执行，
     * token 通过 Sinks 实时推给 SSE 连接，避免阻塞响应。
     * <p>
     * produces 同时声明 JSON：并发超额时需要在返回 Flux 之前抛出 429，
     * 由 GlobalExceptionHandler 以 JSON 形式返回。
     */
    @PostMapping(value = "/stream",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Flux<String> streamPipline(@RequestBody(required = false) MultimodalChatRequest mpc) {

        ChatPipelineContext ctx = buildContext(mpc);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 请求体本身有问题：直接推错误帧，不占用额度
        if (ctx.isInterrupted()) {
            sink.tryEmitNext("[ERROR] " + ctx.getInterruptReason());
            sink.tryEmitNext(DONE);
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        String userId = resolveUserId(ctx);
        String requestId = UUID.randomUUID().toString();
        ctx.setAttribute(ChatPipelineContext.REQUEST_ID, requestId);

        // 会话锁用的原始 sessionId（不能用 resolveUserId 的 anonymous 兜底，原因同 /fack）
        String sessionId = (String) ctx.getAttribute("sessionId");

        boolean res = concurrentLimitService.tryAcquire(userId,requestId);
        if ( !res)
            throw new BusinessException(CODE_TOO_MANY_CONCURRENT ,"您的并发请求过多，请稍后重试") ;

        // 注入流式通道：ChatAgentStage 检测到后改用 StreamingChatModel 逐 token 回调
        ctx.setAttribute(ChatPipelineContext.STREAM_TOKEN_CONSUMER,
                (Consumer<String>) sink::tryEmitNext);

        CompletableFuture.runAsync(() -> {
            boolean sessionLocked = false;
            try {
                   sessionLocked = sessionLockService.tryLockSession(sessionId);
                   if (!sessionLocked){
                       sink.tryEmitNext("[ERROR] 该会话正在处理中，请稍候再试");
                       sink.tryEmitNext(DONE);
                       sink.tryEmitComplete();
                       return;
                   }

                executor.execute(chatPipeline, ctx);
                if (ctx.isInterrupted()) {
                    sink.tryEmitNext("[ERROR] " + ctx.getInterruptReason());
                } else if (ctx.getAttribute("response") == null) {
                    sink.tryEmitNext("[ERROR] 流水线未产生响应");
                }
                sink.tryEmitNext(DONE);
                sink.tryEmitComplete();
            } catch (Exception e) {
                sink.tryEmitError(e);
            } finally {
                if (sessionLocked) { sessionLockService.unlockSession(sessionId); }
                concurrentLimitService.release(userId,requestId);
            }
        });

        return sink.asFlux();
    }

    /**
     * 统一构造 Pipeline 上下文，保证两个接口入参行为一致
     */
    private ChatPipelineContext buildContext(MultimodalChatRequest mpc) {
        ChatPipelineContext ctx = new ChatPipelineContext();
        if (mpc == null) {
            ctx.interrupt("请求体为空");
            return ctx;
        }
        ctx.setAttribute("request", mpc);
        ctx.setAttribute("modelId", mpc.getModelName());
        ctx.setAttribute("sessionId", mpc.getSessionId());
        if (mpc.getAttachments() != null) {
            ctx.setAttachments(mpc.getAttachments());
        }
        ctx.setAttribute("text", mpc.getText());
        return ctx;
    }

    /**
     * 并发限制的"用户"标识：当前项目无 userId 体系，复用 sessionId，缺失时兜底 anonymous，
     */
    private String resolveUserId(ChatPipelineContext ctx) {
        String sessionId = (String) ctx.getAttribute("sessionId");
        return (sessionId == null || sessionId.isBlank()) ? "anonymous" : sessionId;
    }
}
