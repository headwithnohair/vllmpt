package org.albedo.vllmpt.module.approval.core.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.user.mapper.SysUserRoleMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalAdminGuardTest {

    private SysUserRoleMapper roleMapper;
    private ApprovalAdminGuard guard;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysUserRoleMapper.class);
        guard = new ApprovalAdminGuard(roleMapper);
        UserContextHolder.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void adminCanManageTemplates() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(guard::requireAdmin);
    }

    @Test
    void nonAdminIsRejected() {
        when(roleMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, guard::requireAdmin);
        assertEquals(403, ex.getCode());
    }
}
