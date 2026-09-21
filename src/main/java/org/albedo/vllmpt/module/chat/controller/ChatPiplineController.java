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

/**
 * <h3>============ 练习说明 ============</h3>
 * 本类两个接口中标了 TODO 的位置需要你自己补全（对应计划文档的「步骤 5 / 步骤 6」）。
 * {@code buildContext}、requestId 生成、SSE 推送、[DONE] 标记等样板已经就绪，不用动。
 * <p>
 * 补全时务必想清楚这 3 点：
 * <ol>
 *   <li><b>/stream 的 acquire 必须在 {@code return sink.asFlux()} 之前</b> ——
 *       SSE 一旦返回就是 HTTP 200 + text/event-stream，之后再也无法改写成 429。</li>
 *   <li><b>release 必须放在 finally</b>，覆盖「正常 / 中断 / 异常」三条路径，否则一定泄漏额度。</li>
 *   <li>客户端断连时不要提前释放（此时 pipeline 还在跑），靠 ZSet 窗口 + EXPIRE 兜底即可。</li>
 * </ol>
 */
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

        boolean res = concurrentLimitService.tryAcquire(userId,sessionId);
        if ( !res)
            throw new BusinessException(CODE_TOO_MANY_CONCURRENT ,"您的并发请求过多，请稍后重试") ;

        boolean sessionLocked = false;
        try {
            // TODO [步骤2-5] 尝试获取会话锁（同步接口在 Tomcat 线程内加锁，与下面的解锁天然同线程）：
            //   sessionLocked = sessionLockService.tryLockSession(sessionId);
            //   若返回 false，说明该会话已有请求在处理，抛：
            //     new BusinessException(CODE_SESSION_BUSY, "该会话正在处理中，请稍候再试")
            //   注意：throw 必须写在 try 内部，这样 finally 里的额度释放才能兜住这次失败

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
            // TODO [步骤2-6] 释放会话锁（只有真的拿到锁才需要释放）：
            //   if (sessionLocked) { sessionLockService.unlockSession(sessionId); }
            concurrentLimitService.release(userId,sessionId);
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
                // TODO [步骤2-3] 必须在这里（runAsync 内部！）尝试获取会话锁：
                //   sessionLocked = sessionLockService.tryLockSession(sessionId);
                //   获取失败说明该会话已有请求在处理，推错误帧后收尾并 return：
                //     sink.tryEmitNext("[ERROR] 该会话正在处理中，请稍候再试");
                //     sink.tryEmitNext(DONE);
                //     sink.tryEmitComplete();
                //     return;
                //   为什么必须放在 runAsync 内部：RLock 按 clientUuid:threadId 绑定持有者，
                //   若在 Tomcat 线程加锁、到这里（ForkJoinPool 线程）解锁，
                //   isHeldByCurrentThread() 会是 false，unlock() 抛 IllegalMonitorStateException

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
                // TODO [步骤2-4] 释放会话锁（只有真的拿到锁才需要释放）：
                //   if (sessionLocked) { sessionLockService.unlockSession(sessionId); }
                // 下面这行是场景1 的额度释放，锁获取失败时也必须执行，不要删
                concurrentLimitService.release(userId,sessionId);
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
     * 避免写出 {@code ai:concurrent:null} 这种 key。
     * <p>
     * 这里已经写好，注意这个细节：{@code ChatPipelineContext} 在业务代码中以<b>裸类型</b>使用，
     * 而 Java 裸类型会擦除成员泛型，所以 {@code getAttribute(...)} 的返回类型是 Object，必须强转。
     */
    private String resolveUserId(ChatPipelineContext ctx) {
        String sessionId = (String) ctx.getAttribute("sessionId");
        return (sessionId == null || sessionId.isBlank()) ? "anonymous" : sessionId;
    }
}
