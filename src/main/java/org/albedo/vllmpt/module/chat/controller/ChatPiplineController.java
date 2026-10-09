package org.albedo.vllmpt.module.chat.controller;


import dev.langchain4j.model.output.TokenUsage;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.core.order.pipeline.ChatPipeline;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineExecutor;
import org.albedo.vllmpt.module.chat.config.TokenQuotaProperties;
import org.albedo.vllmpt.module.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.module.chat.model.vo.QuotaReservation;
import org.albedo.vllmpt.module.chat.service.ChatSessionLockService;
import org.albedo.vllmpt.module.chat.service.ConcurrentLimitService;
import org.albedo.vllmpt.module.chat.service.TokenQuotaService;
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

    @Autowired
    private TokenQuotaService tokenQuotaService;

    @Autowired
    private TokenQuotaProperties tokenQuotaProperties;

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

        // finally 里结算要用：预扣凭证 + 真实用量
        QuotaReservation reservation = null;
        TokenUsage usage = null;

        boolean sessionLocked = false;
        try {
               reservation = tokenQuotaService.preDeduct(userId, tokenQuotaProperties.getEstimateTokens());


            sessionLocked=sessionLockService.tryLockSession(sessionId);
            if (!sessionLocked){
                throw new BusinessException(CODE_SESSION_BUSY, "该会话正在处理中，请稍候再试");
            }
            executor.execute(chatPipeline, ctx);

               usage = (TokenUsage) ctx.getAttribute(ChatPipelineContext.TOKEN_USAGE);

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
               if (sessionLocked) {
                   tokenQuotaService.settle(reservation, usage, resolveModelName(ctx));
               } else {
                   // 会话锁没拿到 → 流水线根本没跑 → 不能扣费，全额退还预扣
                   tokenQuotaService.refund(reservation);
               }
            //   finally 里一条语句抛异常，后面的都不会执行 ——
            //   而 settle / refund 内部已经吞掉全部异常（这是它们的实现要求），
            //   所以不会阻断下面最关键的那行 release。
            //   预扣没成功时 reservation 为 null，settle 与 refund 都会直接返回。

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

        QuotaReservation reservation = preDeductOrRelease(userId, requestId);


        // 注入流式通道：ChatAgentStage 检测到后改用 StreamingChatModel 逐 token 回调
        ctx.setAttribute(ChatPipelineContext.STREAM_TOKEN_CONSUMER,
                (Consumer<String>) sink::tryEmitNext);

        CompletableFuture.runAsync(() -> {
            boolean sessionLocked = false;
            TokenUsage usage = null;
            try {
                   sessionLocked = sessionLockService.tryLockSession(sessionId);
                   if (!sessionLocked){
                       sink.tryEmitNext("[ERROR] 该会话正在处理中，请稍候再试");
                       sink.tryEmitNext(DONE);
                       sink.tryEmitComplete();
                       return;
                   }

                executor.execute(chatPipeline, ctx);

                   usage = (TokenUsage) ctx.getAttribute(ChatPipelineContext.TOKEN_USAGE);

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


                   if (sessionLocked) {
                       tokenQuotaService.settle(reservation, usage, resolveModelName(ctx));
                   } else {
                       tokenQuotaService.refund(reservation);
                   }
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
     * 并发限制的"用户"标识：当前项目无 userId 体系，优先取请求体里显式传入的 userId，缺失时退回 sessionId，再兜底 anonymous，
     */
    private String resolveUserId(ChatPipelineContext ctx) {
        MultimodalChatRequest request = (MultimodalChatRequest) ctx.getAttribute("request");
        if (request != null && request.getUserId() != null && !request.getUserId().isBlank()) {
            return request.getUserId().trim();
        }
        String sessionId = (String) ctx.getAttribute("sessionId");
        return (sessionId == null || sessionId.isBlank()) ? "anonymous" : sessionId;
    }

    /**
     * 结算与落库用的模型名。
     * <p>
     * 优先取上下文里的 {@code modelName}（Stage 可能改写），退回请求里的 {@code modelName}，
     * 最后兜底 {@code "unknown"} —— 表里 {@code model_name} 是唯一键的一部分，不能为 null。
     */
    private String resolveModelName(ChatPipelineContext ctx) {
        String modelName = ctx.getModelName();
        if (modelName != null && !modelName.isBlank()) {
            return modelName;
        }
        Object fromRequest = ctx.getAttribute("modelId");
        return fromRequest == null ? "unknown" : fromRequest.toString();
    }

    /**
     * 预扣当日 Token 额度；失败时先退还刚占用的并发额度，再把 402 抛出去。
     * <p>
     * 抽成方法，是为了让 {@code /stream} 里能写成「一次赋值」的形式
     * （{@code QuotaReservation reservation = preDeductOrRelease(userId, requestId);}），
     * 这样它天然可以被 runAsync 的 lambda 捕获。
     * <p>
     * 顺序是「并发额度 → 预扣 → 会话锁」，所以预扣失败时必须退还并发额度，
     * 否则额度只增不减，用户会被自己失败的请求挤爆。
     */
    private QuotaReservation preDeductOrRelease(String userId, String requestId) {
        try {
            return tokenQuotaService.preDeduct(userId, tokenQuotaProperties.getEstimateTokens());
        } catch (RuntimeException e) {
            concurrentLimitService.release(userId, requestId);
            throw e;
        }
    }
}
