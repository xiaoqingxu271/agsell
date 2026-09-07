package com.lichun.agsell.service.impl;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.AdminUserStatusRequest;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.service.RedisTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 链路 B 修复验证：禁用用户踢下线、用户存在校验
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private RedisTokenService redisTokenService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Test
    @DisplayName("禁用用户时应删除其全部登录 token（踢下线）")
    void disableUserKicksOutAllTokens() {
        SysUser exist = new SysUser();
        exist.setId(10L);
        exist.setStatus(1);
        when(sysUserMapper.selectById(10L)).thenReturn(exist);
        when(sysUserMapper.updateById((SysUser) any())).thenReturn(1);

        AdminUserStatusRequest request = new AdminUserStatusRequest();
        request.setStatus(0);
        adminUserService.updateUserStatus(10L, request);

        verify(redisTokenService).deleteUserTokens(10L);
        verify(sysUserMapper).updateById(argThat((SysUser u) -> u.getStatus() == 0));
    }

    @Test
    @DisplayName("启用用户时不应删除 token")
    void enableUserKeepsTokens() {
        SysUser exist = new SysUser();
        exist.setId(10L);
        exist.setStatus(0);
        when(sysUserMapper.selectById(10L)).thenReturn(exist);
        when(sysUserMapper.updateById((SysUser) any())).thenReturn(1);

        AdminUserStatusRequest request = new AdminUserStatusRequest();
        request.setStatus(1);
        adminUserService.updateUserStatus(10L, request);

        verify(redisTokenService, never()).deleteUserTokens(anyLong());
    }

    @Test
    @DisplayName("用户不存在时报错")
    void updateNonExistentUserFails() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        AdminUserStatusRequest request = new AdminUserStatusRequest();
        request.setStatus(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUserStatus(999L, request));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        verify(sysUserMapper, never()).updateById((SysUser) any());
    }

    @Test
    @DisplayName("状态值不合法时报参数错误")
    void invalidStatusRejected() {
        AdminUserStatusRequest request = new AdminUserStatusRequest();
        request.setStatus(2);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUserStatus(1L, request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }
}
