package org.albedo.vllmpt.module.approval.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.spi.AiDecision;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 审批 AI 决策指标。
 * <p>
 * Prometheus 示例：
 * <ul>
 *   <li>自动批率：approve 计数 / 全部计数；</li>
 *   <li>升级率：escalate 计数 / 全部计数；</li>
 *   <li>平均耗时：timer 的 sum/count。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalAiMetrics {

    private final MeterRegistry meterRegistry;

    public void record(AiDecision decision, long latencyMs) {
        try {
            String tag = decision.name();
            Counter.builder("approval.ai.decision")
                    .description("AI approval decisions by result")
                    .tag("decision", tag)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("approval.ai.decision.latency")
                    .description("AI approval decision latency")
                    .tag("decision", tag)
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 指标失败不能让审批事务回滚
            log.warn("[审批AI] 指标记录失败: decision={}, error={}", decision, e.getMessage());
        }
    }
}
