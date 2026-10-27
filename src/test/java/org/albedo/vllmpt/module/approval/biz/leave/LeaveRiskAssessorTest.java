package org.albedo.vllmpt.module.approval.biz.leave;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.albedo.vllmpt.module.approval.biz.leave.domain.enums.LeaveType;
import org.albedo.vllmpt.module.approval.biz.leave.domain.model.LeavePolicy;
import org.albedo.vllmpt.module.approval.biz.leave.service.LeaveRiskAssessor;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.spi.LlmAssessment;
import org.albedo.vllmpt.module.approval.core.spi.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveRiskAssessorTest {

    private ChatModelFactory chatModelFactory;
    private ChatModel chatModel;
    private ApprovalAiProperties properties;
    private LeaveRiskAssessor assessor;

    @BeforeEach
    void setUp() {
        chatModelFactory = mock(ChatModelFactory.class);
        chatModel = mock(ChatModel.class);
        properties = new ApprovalAiProperties();
        properties.setModelName("m");
        properties.setMaxTokens(1024);
        properties.setTimeoutSeconds(5);
        assessor = new LeaveRiskAssessor(chatModelFactory, properties, new com.fasterxml.jackson.databind.ObjectMapper());
        when(chatModelFactory.createModel(anyString(), anyDouble(), anyInt(), anyInt())).thenReturn(chatModel);
    }

    @Test
    void parsesLowRiskJson() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from(
                "```json\n{\"risk\":\"LOW\",\"confidence\":0.92,\"reason\":\"事实完整\"}\n```"));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        LlmAssessment result = assessor.assess(Map.of("leaveDays", 1),
                LeavePolicy.defaults(LeaveType.ANNUAL), List.of());

        assertFalse(result.failed());
        assertEquals(RiskLevel.LOW, result.risk());
        assertEquals(0.92, result.confidence(), 0.001);
        assertEquals("m", result.modelName());
    }

    @Test
    void invalidJsonFallsBackToFailed() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from("not a json"));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        LlmAssessment result = assessor.assess(Map.of("leaveDays", 1),
                LeavePolicy.defaults(LeaveType.ANNUAL), List.of());

        assertTrue(result.failed());
        assertEquals(RiskLevel.HIGH, result.risk());
    }

    @Test
    void disabledAiSkipsModelCall() {
        properties.setEnabled(false);

        LlmAssessment result = assessor.assess(Map.of("leaveDays", 1),
                LeavePolicy.defaults(LeaveType.ANNUAL), List.of());

        assertTrue(result.failed());
        verify(chatModelFactory, never()).createModel(anyString(), anyDouble(), anyInt(), anyInt());
    }
}
