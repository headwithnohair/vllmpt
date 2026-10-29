package org.albedo.vllmpt.module.approval.core.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalCc;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.albedo.vllmpt.module.approval.core.engine.ApprovalEngine;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalAiDecisionMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalCcMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateMapper;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalBizRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalInstanceServiceAccessTest {

    private ApprovalInstanceMapper instanceMapper;
    private ApprovalTaskMapper taskMapper;
    private ApprovalCcMapper ccMapper;
    private ApprovalInstanceService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApprovalTask.class);
        TableInfoHelper.initTableInfo(assistant, ApprovalCc.class);
    }

    @BeforeEach
    void setUp() {
        instanceMapper = mock(ApprovalInstanceMapper.class);
        taskMapper = mock(ApprovalTaskMapper.class);
        ccMapper = mock(ApprovalCcMapper.class);
        service = new ApprovalInstanceService(
                instanceMapper,
                mock(ApprovalTemplateMapper.class),
                taskMapper,
                mock(ApprovalRecordMapper.class),
                ccMapper,
                mock(ApprovalAiDecisionMapper.class),
                mock(ApprovalTemplateService.class),
                mock(ApprovalEngine.class),
                mock(FormSchemaValidator.class),
                new ObjectMapper(),
                mock(ApprovalNumberGenerator.class),
                mock(ApprovalIdempotencyGuard.class),
                mock(ApprovalBizRegistry.class));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void detailRejectsUnrelatedUser() {
        UserContextHolder.setUserId(2L);
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(10L);
        instance.setApplicantId(1L);
        when(instanceMapper.selectById(10L)).thenReturn(instance);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(ccMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(10L));

        assertEquals(403, ex.getCode());
    }

    @Test
    void cancelRejectsNonApplicant() {
        UserContextHolder.setUserId(2L);
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(10L);
        instance.setApplicantId(1L);
        when(instanceMapper.selectById(10L)).thenReturn(instance);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10L, "越权尝试"));

        assertEquals(403, ex.getCode());
    }
}
