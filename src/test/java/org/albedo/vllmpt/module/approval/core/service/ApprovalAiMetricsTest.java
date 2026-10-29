package org.albedo.vllmpt.module.approval.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.albedo.vllmpt.module.approval.core.spi.AiDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApprovalAiMetricsTest {

    @Test
    void recordsDecisionCounterAndLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalAiMetrics metrics = new ApprovalAiMetrics(registry);

        metrics.record(AiDecision.APPROVE, 120L);
        metrics.record(AiDecision.ESCALATE, 80L);

        Counter approve = registry.find("approval.ai.decision").tag("decision", "APPROVE").counter();
        assertNotNull(approve);
        assertEquals(1.0, approve.count(), 0.001);

        Timer latency = registry.find("approval.ai.decision.latency").tag("decision", "APPROVE").timer();
        assertNotNull(latency);
        assertEquals(1, latency.count());
        assertEquals(120.0, latency.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 0.001);
    }
}
