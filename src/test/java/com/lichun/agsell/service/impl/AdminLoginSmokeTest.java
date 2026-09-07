package com.lichun.agsell.service.impl;

import com.lichun.agsell.model.dto.AdminLoginRequest;
import com.lichun.agsell.model.vo.AdminLoginVO;
import com.lichun.agsell.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冒烟验证：管理端登录在真实 MySQL/Redis 环境下是否正常（排查运行实例 login 50000）
 */
@SpringBootTest
class AdminLoginSmokeTest {

    @Autowired
    private AdminAuthService adminAuthService;

    @Test
    @DisplayName("真实环境：admin 登录应成功并返回 token")
    void loginShouldWork() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        AdminLoginVO vo = adminAuthService.login(req);

        assertNotNull(vo.getToken());
        assertTrue(vo.getToken().length() > 20);
        assertNotNull(vo.getRole());
    }
}
