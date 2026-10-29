package org.albedo.vllmpt.module.approval.core.engine.node;

import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResultType;
import org.albedo.vllmpt.module.approval.core.service.ApprovalAiDecisionService;
import org.albedo.vllmpt.module.approval.core.spi.AiDecision;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionResult;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalAiDecisionProvider;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalAiDecisionRegistry;
import org.albedo.vllmpt.module.approval.core.spi.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDecisionNodeHandlerTest {

    private ApprovalAiDecisionRegistry registry;
    private ApprovalAiDecisionService decisionService;
    private ApprovalAiDecisionProvider provider;
    private ApprovalAiProperties properties;
    private AiDecisionNodeHandler handler;

    @BeforeEach
    void setUp() {
        registry = mock(ApprovalAiDecisionRegistry.class);
        decisionService = mock(ApprovalAiDecisionService.class);
        provider = mock(ApprovalAiDecisionProvider.class);
        properties = new ApprovalAiProperties();
        handler = new AiDecisionNodeHandler(registry, decisionService, properties);
    }

    @Test
    void approveRoutesToConfiguredNode() {
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");
        when(registry.find("leavePolicy")).thenReturn(provider);
        when(provider.decide(any())).thenReturn(AiDecisionResult.approve(
                0.95, RiskLevel.LOW, "ok", List.of(), Map.of(), "m", "v1"));

        NodeResult result = handler.handle(context(node), node);

        assertEquals(NodeResultType.PASS, result.type());
        assertEquals("cc_hr", result.nextNodeKey());
        verify(decisionService).save(any(), eq(node), any(), anyLong());
    }

    @Test
    void lowConfidenceApprovalIsDowngradedToEscalate() {
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");
        when(registry.find("leavePolicy")).thenReturn(provider);
        when(provider.decide(any())).thenReturn(AiDecisionResult.approve(
                0.50, RiskLevel.LOW, "not sure", List.of(), Map.of(), "m", "v1"));

        NodeResult result = handler.handle(context(node), node);

        assertEquals(NodeResultType.PASS, result.type());
        assertEquals("manager_approval", result.nextNodeKey());
        ArgumentCaptor<AiDecisionResult> captor = ArgumentCaptor.forClass(AiDecisionResult.class);
        verify(decisionService).save(any(), eq(node), captor.capture(), anyLong());
        assertEquals(AiDecision.ESCALATE, captor.getValue().decision());
    }

    @Test
    void aiDisabledEscalatesWithoutCallingProvider() {
        properties.setEnabled(false);
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");

        NodeResult result = handler.handle(context(node), node);

        assertEquals("manager_approval", result.nextNodeKey());
        verify(registry, never()).find(any());
        ArgumentCaptor<AiDecisionResult> captor = ArgumentCaptor.forClass(AiDecisionResult.class);
        verify(decisionService).save(any(), eq(node), captor.capture(), anyLong());
        assertEquals(AiDecision.ESCALATE, captor.getValue().decision());
    }

    @Test
    void grayMissEscalatesWithoutCallingProvider() {
        properties.setRolloutPercent(0);
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");

        NodeResult result = handler.handle(context(node), node);

        assertEquals("manager_approval", result.nextNodeKey());
        verify(registry, never()).find(any());
    }

    @Test
    void missingProviderEscalates() {
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");
        when(registry.find("leavePolicy")).thenReturn(null);

        NodeResult result = handler.handle(context(node), node);

        assertEquals("manager_approval", result.nextNodeKey());
        verify(decisionService).save(any(), eq(node), any(), anyLong());
    }

    @Test
    void escalateWithoutTargetFailsClosed() {
        ApprovalTemplateNode node = node("cc_hr", null, "REJECT");
        when(registry.find("leavePolicy")).thenReturn(provider);
        when(provider.decide(any())).thenReturn(AiDecisionResult.escalate(
                0.0, RiskLevel.HIGH, "need human", List.of(), Map.of(), "m", "v1"));

        assertThrows(BusinessException.class, () -> handler.handle(context(node), node));
        verify(decisionService, never()).save(any(), any(), any(), anyLong());
    }

    @Test
    void rejectWithNextNodeKeepsFlowRunning() {
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "NEXT_NODE");
        when(registry.find("leavePolicy")).thenReturn(provider);
        when(provider.decide(any())).thenReturn(AiDecisionResult.reject(
                1.0, RiskLevel.HIGH, "bad", List.of(), "v1"));

        NodeResult result = handler.handle(context(node), node);

        assertEquals(NodeResultType.PASS, result.type());
        assertEquals(null, result.nextNodeKey());
    }

    @Test
    void rejectWithRejectActionStopsInstance() {
        ApprovalTemplateNode node = node("cc_hr", "manager_approval", "REJECT");
        when(registry.find("leavePolicy")).thenReturn(provider);
        when(provider.decide(any())).thenReturn(AiDecisionResult.reject(
                1.0, RiskLevel.HIGH, "bad", List.of(), "v1"));

        NodeResult result = handler.handle(context(node), node);

        assertEquals(NodeResultType.REJECT, result.type());
        assertEquals("bad", result.comment());
    }

    private ApprovalTemplateNode node(String approveKey, String escalateKey, String rejectAction) {
        ApprovalTemplateNode node = new ApprovalTemplateNode();
        node.setNodeKey("ai_decision");
        node.setAiPolicyKey("leavePolicy");
        node.setAiMinConfidence(new BigDecimal("0.80"));
        node.setAiOnApproveNodeKey(approveKey);
        node.setAiOnEscalateNodeKey(escalateKey);
        node.setAiOnRejectAction(rejectAction);
        return node;
    }

    private NodeHandleContext context(ApprovalTemplateNode node) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(1L);
        instance.setBizType("LEAVE");
        instance.setFacts("{}");
        return new NodeHandleContext(instance, List.of(node), Map.of());
    }
}
