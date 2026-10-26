package org.albedo.vllmpt.module.approval.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.service.ApprovalDraftService;
import org.albedo.vllmpt.module.approval.core.service.ApprovalInstanceService;
import org.albedo.vllmpt.module.approval.core.service.ApprovalTemplateService;
import org.albedo.vllmpt.module.chat.tool.ToolContext;
import org.albedo.vllmpt.module.chat.tool.ToolContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalToolProviderTest {

    private ApprovalDraftService draftService;
    private ApprovalInstanceService instanceService;
    private ApprovalTemplateService templateService;
    private ApprovalToolProvider provider;

    @BeforeEach
    void setUp() {
        draftService = mock(ApprovalDraftService.class);
        instanceService = mock(ApprovalInstanceService.class);
        templateService = mock(ApprovalTemplateService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        provider = new ApprovalToolProvider(draftService, instanceService, templateService, objectMapper);

        UserContextHolder.setUserId(1L);
        ToolContextHolder.set(new ToolContext(1L, "s1", "LEAVE", "approval"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        ToolContextHolder.clear();
    }

    @Test
    void saveDraftMergesAndReturnsMissingFields() {
        ApprovalDraftVO draft = new ApprovalDraftVO("LEAVE", "s1",
                Map.of("leaveType", "ANNUAL"), List.of("startTime", "reason"), LocalDateTime.now());
        when(draftService.save(any(), any())).thenReturn(draft);

        String result = provider.saveApprovalDraft("{\"leaveType\":\"ANNUAL\"}");

        assertTrue(result.contains("\"saved\":true"));
        assertTrue(result.contains("startTime"));
        verify(draftService).save(eq(1L), any());
    }

    @Test
    void submitDraftCallsInstanceServiceAndDeletesDraft() {
        ApprovalDraftVO draft = new ApprovalDraftVO("LEAVE", "s1",
                Map.of("leaveType", "ANNUAL"), List.of(), LocalDateTime.now());
        when(draftService.get(1L, "LEAVE", "s1")).thenReturn(draft);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setName("请假申请");
        template.setBizType("LEAVE");
        when(templateService.findPublishedByBizType("LEAVE")).thenReturn(template);
        when(instanceService.submit(any())).thenReturn(10L);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(10L);
        instance.setInstanceNo("AP20261026000001");
        instance.setStatus("RUNNING");
        instance.setCurrentNodeName("直属主管审批");
        InstanceDetailVO detail = new InstanceDetailVO();
        detail.setInstance(instance);
        when(instanceService.detail(10L)).thenReturn(detail);

        String result = provider.submitApprovalDraft();

        assertTrue(result.contains("\"submitted\":true"));
        assertTrue(result.contains("AP20261026000001"));
        verify(draftService).delete(1L, "LEAVE", "s1");
    }

    @Test
    void submitRejectsIncompleteDraft() {
        ApprovalDraftVO draft = new ApprovalDraftVO("LEAVE", "s1",
                Map.of("leaveType", "ANNUAL"), List.of("reason"), LocalDateTime.now());
        when(draftService.get(1L, "LEAVE", "s1")).thenReturn(draft);

        BusinessException ex = assertThrows(BusinessException.class, provider::submitApprovalDraft);
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("reason"));
    }

    @Test
    void submitRejectsMissingDraft() {
        when(draftService.get(1L, "LEAVE", "s1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, provider::submitApprovalDraft);
        assertEquals(400, ex.getCode());
    }

    @Test
    void queryReturnsMyApprovals() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(3L);
        instance.setInstanceNo("AP3");
        instance.setTitle("请假申请");
        instance.setStatus("RUNNING");
        when(instanceService.my("LEAVE", "RUNNING", 10)).thenReturn(List.of(instance));

        String result = provider.queryMyApprovals("RUNNING");

        assertTrue(result.contains("\"count\":1"));
        assertTrue(result.contains("AP3"));
    }

    @Test
    void cancelDelegatesToInstanceService() {
        String result = provider.cancelApproval(10L);

        assertTrue(result.contains("\"canceled\":true"));
        verify(instanceService).cancel(10L, "AI 对话撤回");
    }

    @Test
    void invalidFormJsonRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.saveApprovalDraft("[]"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void missingBizTypeRejected() {
        ToolContextHolder.set(new ToolContext(1L, "s1", null, "approval"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.saveApprovalDraft("{\"leaveType\":\"ANNUAL\"}"));
        assertEquals(400, ex.getCode());
    }
}
