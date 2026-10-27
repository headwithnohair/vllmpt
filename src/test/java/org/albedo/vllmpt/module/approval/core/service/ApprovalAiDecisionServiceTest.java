package org.albedo.vllmpt.module.approval.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalAiDecision;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAction;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalAiDecisionMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionResult;
import org.albedo.vllmpt.module.approval.core.spi.RiskLevel;
import org.albedo.vllmpt.module.approval.core.spi.RuleHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalAiDecisionServiceTest {

    private ApprovalAiDecisionMapper decisionMapper;
    private ApprovalRecordMapper recordMapper;
    private ApprovalAiDecisionService service;

    @BeforeEach
    void setUp() {
        decisionMapper = mock(ApprovalAiDecisionMapper.class);
        recordMapper = mock(ApprovalRecordMapper.class);
        when(decisionMapper.selectCount(any())).thenReturn(0L);
        ApprovalAiProperties properties = new ApprovalAiProperties();
        properties.setPromptVersion("v1");
        service = new ApprovalAiDecisionService(decisionMapper, recordMapper,
                new ObjectMapper(), properties);
    }

    @Test
    void savesSnapshotAndAiRecord() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(1L);
        instance.setFacts("{\"leaveDays\":1}");
        ApprovalTemplateNode node = new ApprovalTemplateNode();
        node.setNodeKey("ai_decision");
        node.setAiPolicyKey("leavePolicy");

        AiDecisionResult result = AiDecisionResult.approve(0.95, RiskLevel.LOW, "ok",
                List.of(RuleHit.pass("R1", "通过")), Map.of("risk", "LOW"), "m", "v1");

        service.save(instance, node, result, 123L);

        ArgumentCaptor<ApprovalAiDecision> decisionCaptor = ArgumentCaptor.forClass(ApprovalAiDecision.class);
        verify(decisionMapper).insert(decisionCaptor.capture());
        ApprovalAiDecision saved = decisionCaptor.getValue();
        assertEquals(1, saved.getDecisionNo());
        assertEquals("APPROVE", saved.getDecision());
        assertEquals(0, saved.getConfidence().compareTo(new java.math.BigDecimal("0.950")));
        assertEquals("leavePolicy", saved.getPolicyKey());
        assertEquals("m", saved.getModelName());
        assertEquals("v1", saved.getPolicyVersion());
        assertEquals(123, saved.getLatencyMs());

        ArgumentCaptor<ApprovalRecord> recordCaptor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(ApprovalAction.AI_APPROVE.name(), recordCaptor.getValue().getAction());
        assertEquals("ok", recordCaptor.getValue().getComment());
    }
}
