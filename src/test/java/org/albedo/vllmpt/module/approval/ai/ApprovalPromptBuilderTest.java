package org.albedo.vllmpt.module.approval.ai;

import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.service.ApprovalTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalPromptBuilderTest {

    private ApprovalTemplateService templateService;
    private ApprovalPromptBuilder builder;

    @BeforeEach
    void setUp() {
        templateService = mock(ApprovalTemplateService.class);
        builder = new ApprovalPromptBuilder(templateService);
    }

    @Test
    void buildsPromptWithSchemaAndRules() {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setName("请假申请");
        template.setBizType("LEAVE");
        template.setFormSchema("{\"fields\":[{\"name\":\"leaveType\",\"required\":true}]}");
        when(templateService.findPublishedByBizType("LEAVE")).thenReturn(template);

        String prompt = builder.build("LEAVE");

        assertTrue(prompt.contains("请假申请"));
        assertTrue(prompt.contains("leaveType"));
        assertTrue(prompt.contains("不得猜测"));
        assertTrue(prompt.contains("submitApprovalDraft"));
        assertTrue(prompt.contains("bizType 由服务端注入"));
    }

    @Test
    void missingTemplateThrows404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> builder.build("UNKNOWN"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void blankBizTypeThrows400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> builder.build(" "));
        assertEquals(400, ex.getCode());
    }
}
