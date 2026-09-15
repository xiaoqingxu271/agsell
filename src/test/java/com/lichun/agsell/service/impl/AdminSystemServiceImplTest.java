package com.lichun.agsell.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysAdminMapper;
import com.lichun.agsell.model.dto.AdminCreateRequest;
import com.lichun.agsell.model.dto.AdminPasswordResetRequest;
import com.lichun.agsell.model.dto.AdminUpdateRequest;
import com.lichun.agsell.model.entity.SysAdmin;
import com.lichun.agsell.model.vo.AdminListItemVO;
import com.lichun.agsell.service.RedisTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 管理员管理边界测试：参数校验、BCrypt 加密、R1/R2/R3/R4 保护规则
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemServiceImplTest {

    @Mock
    private SysAdminMapper adminMapper;

    @Mock
    private RedisTokenService redisTokenService;

    @InjectMocks
    private AdminSystemServiceImpl adminSystemService;

    @AfterEach
    void tearDown() {
        AdminContext.removeCurrentAdmin();
    }

    private SysAdmin superAdmin() {
        SysAdmin admin = new SysAdmin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole("SUPER_ADMIN");
        admin.setStatus(1);
        return admin;
    }

    private SysAdmin operator(Long id) {
        SysAdmin admin = new SysAdmin();
        admin.setId(id);
        admin.setUsername("op" + id);
        admin.setRole("OPERATOR");
        admin.setStatus(1);
        return admin;
    }

    // ========== createAdmin ==========

    @Test
    @DisplayName("新增：用户名/密码为空、密码少于6位、非法角色均拒绝")
    void createInvalidParamsRejected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        AdminCreateRequest req = new AdminCreateRequest();

        req.setUsername("");
        req.setPassword("123456");
        req.setRole("OPERATOR");
        assertThrows(BusinessException.class, () -> adminSystemService.createAdmin(req));

        req.setUsername("op1");
        req.setPassword("123");
        req.setRole("OPERATOR");
        assertThrows(BusinessException.class, () -> adminSystemService.createAdmin(req));

        req.setUsername("op1");
        req.setPassword("123456");
        req.setRole("SUPER_ADMIN"); // 不允许创建第二个超级管理员
        assertThrows(BusinessException.class, () -> adminSystemService.createAdmin(req));

        verifyNoInteractions(adminMapper);
    }

    @Test
    @DisplayName("新增：用户名重复拒绝（先清理同名已删记录，再校验活动记录）")
    void createDuplicateUsernameRejected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        AdminCreateRequest req = new AdminCreateRequest();
        req.setUsername("admin");
        req.setPassword("123456");
        req.setRole("OPERATOR");
        when(adminMapper.purgeDeletedByUsername("admin")).thenReturn(1);
        when(adminMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.createAdmin(req));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增：正常创建，密码 BCrypt 加密（非明文且可校验）")
    void createEncryptsPassword() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        AdminCreateRequest req = new AdminCreateRequest();
        req.setUsername("op1");
        req.setPassword("op123456");
        req.setRole("OPERATOR");
        when(adminMapper.purgeDeletedByUsername("op1")).thenReturn(0);
        when(adminMapper.selectCount(any())).thenReturn(0L);

        adminSystemService.createAdmin(req);

        ArgumentCaptor<SysAdmin> captor = ArgumentCaptor.forClass(SysAdmin.class);
        verify(adminMapper).insert(captor.capture());
        SysAdmin saved = captor.getValue();
        assertNotEquals("op123456", saved.getPassword(), "密码不得明文存储");
        assertTrue(BCrypt.checkpw("op123456", saved.getPassword()), "BCrypt 校验必须通过");
        assertEquals("OPERATOR", saved.getRole());
        assertEquals(1, saved.getStatus());
    }

    // ========== updateAdmin ==========

    @Test
    @DisplayName("编辑：操作当前登录账号被拒绝（R1）")
    void updateSelfRejected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        when(adminMapper.selectById(1L)).thenReturn(superAdmin());
        AdminUpdateRequest req = new AdminUpdateRequest();
        req.setRealName("改名");
        req.setRole("OPERATOR");
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.updateAdmin(1L, req));
        assertEquals("不能修改当前登录账号", ex.getMessage());
    }

    @Test
    @DisplayName("编辑：非法角色拒绝（R4），正常编辑通过")
    void updateRoleWhiteListAndSuccess() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        when(adminMapper.selectById(2L)).thenReturn(operator(2L));

        AdminUpdateRequest bad = new AdminUpdateRequest();
        bad.setRole("SUPER_ADMIN");
        assertThrows(BusinessException.class, () -> adminSystemService.updateAdmin(2L, bad));

        AdminUpdateRequest ok = new AdminUpdateRequest();
        ok.setRealName("小李");
        ok.setRole("ADMIN");
        adminSystemService.updateAdmin(2L, ok);
        ArgumentCaptor<SysAdmin> captor = ArgumentCaptor.forClass(SysAdmin.class);
        verify(adminMapper).updateById(captor.capture());
        assertEquals("ADMIN", captor.getValue().getRole());
        assertEquals("小李", captor.getValue().getRealName());
    }

    // ========== resetPassword ==========

    @Test
    @DisplayName("重置密码：新密码为空/少于6位/目标不存在均拒绝，正常则加密")
    void resetPasswordValidates() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        AdminPasswordResetRequest req = new AdminPasswordResetRequest();
        req.setNewPassword("123");
        assertThrows(BusinessException.class, () -> adminSystemService.resetPassword(2L, req));

        when(adminMapper.selectById(2L)).thenReturn(operator(2L));
        req.setNewPassword("newpass123");
        adminSystemService.resetPassword(2L, req);
        ArgumentCaptor<SysAdmin> captor = ArgumentCaptor.forClass(SysAdmin.class);
        verify(adminMapper).updateById(captor.capture());
        assertTrue(BCrypt.checkpw("newpass123", captor.getValue().getPassword()));
    }

    // ========== updateStatus ==========

    @Test
    @DisplayName("启停：状态值非法、禁用自己均拒绝")
    void updateStatusRejectsInvalid() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        assertThrows(BusinessException.class, () -> adminSystemService.updateStatus(2L, 9));

        when(adminMapper.selectById(1L)).thenReturn(superAdmin());
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.updateStatus(1L, 0));
        assertEquals("不能禁用当前登录账号", ex.getMessage());
    }

    @Test
    @DisplayName("启停：禁用唯一超级管理员被拒绝（R2），禁用运营正常")
    void updateStatusSuperAdminProtected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        // 目标为另一个超管（防御分支），且系统中仅 1 个超管
        SysAdmin otherSuper = superAdmin();
        otherSuper.setId(99L);
        when(adminMapper.selectById(99L)).thenReturn(otherSuper);
        when(adminMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.updateStatus(99L, 0));
        assertEquals("至少保留一个超级管理员", ex.getMessage());

        // 禁用普通运营正常
        when(adminMapper.selectById(2L)).thenReturn(operator(2L));
        adminSystemService.updateStatus(2L, 0);
        ArgumentCaptor<SysAdmin> captor = ArgumentCaptor.forClass(SysAdmin.class);
        verify(adminMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    // ========== deleteAdmin ==========

    @Test
    @DisplayName("删除：删除自己被拒绝（R1）")
    void deleteSelfRejected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        when(adminMapper.selectById(1L)).thenReturn(superAdmin());
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.deleteAdmin(1L));
        assertEquals("不能删除当前登录账号", ex.getMessage());
    }

    @Test
    @DisplayName("删除：删除唯一超级管理员被拒绝（R2）")
    void deleteLastSuperAdminRejected() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        SysAdmin otherSuper = superAdmin();
        otherSuper.setId(99L);
        when(adminMapper.selectById(99L)).thenReturn(otherSuper);
        when(adminMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> adminSystemService.deleteAdmin(99L));
        assertEquals("至少保留一个超级管理员", ex.getMessage());
    }

    @Test
    @DisplayName("删除：删除低角色管理员正常（逻辑删除）")
    void deleteOperatorSuccess() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        when(adminMapper.selectById(2L)).thenReturn(operator(2L));
        adminSystemService.deleteAdmin(2L);
        verify(adminMapper).deleteById(2L);
    }

    // ========== listAdmins ==========

    @Test
    @DisplayName("列表：VO 脱敏（不含 password 字段）")
    void listAdminsNoPasswordLeak() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        SysAdmin admin = superAdmin();
        admin.setPassword("$2a$10$secret-hash");
        Page<SysAdmin> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(admin));
        when(adminMapper.selectPage(any(), any())).thenReturn(page);

        Page<AdminListItemVO> result = adminSystemService.listAdmins(1, 10, null);
        assertEquals(1, result.getRecords().size());
        AdminListItemVO vo = result.getRecords().get(0);
        assertEquals("admin", vo.getUsername());
        assertEquals("SUPER_ADMIN", vo.getRole());
        boolean hasPasswordField = java.util.Arrays.stream(vo.getClass().getDeclaredFields())
                .anyMatch(f -> f.getName().equals("password"));
        assertFalse(hasPasswordField, "VO 不得包含 password 字段");
    }

    @Test
    @DisplayName("列表：keyword 为空时不加 like 条件，分页参数透传")
    void listAdminsEmptyKeyword() {
        AdminContext.setCurrentAdmin(1L, "SUPER_ADMIN");
        Page<SysAdmin> page = new Page<>(2, 20, 0);
        page.setRecords(List.of());
        when(adminMapper.selectPage(any(), any())).thenReturn(page);
        Page<AdminListItemVO> result = adminSystemService.listAdmins(2, 20, null);
        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getSize());
    }
}
