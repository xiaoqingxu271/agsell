package com.lichun.agsell.config;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.RedisTokenService;
import com.lichun.agsell.utils.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 链路 B 修复验证：拦截器接口级角色鉴权（普通用户 token 禁止访问管理端接口）
 */
class JwtAuthInterceptorTest {

    /** 与 application.yaml 一致的 64 字节密钥（仅测试用） */
    private static final String TEST_SECRET =
            "test-secret-test-secret-test-secret-test-secret-test-secret-test-secret-123456";

    private JwtUtils jwtUtils;
    private RedisTokenService redisTokenService;
    private JwtAuthInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "expiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtils, "tokenHeader", "Authorization");
        ReflectionTestUtils.setField(jwtUtils, "tokenPrefix", "Bearer");

        redisTokenService = mock(RedisTokenService.class);
        when(redisTokenService.validateAdminToken(anyLong(), anyString(), anyString())).thenReturn(true);
        when(redisTokenService.validateUserToken(anyLong(), anyString(), anyString())).thenReturn(true);

        interceptor = new JwtAuthInterceptor(jwtUtils, redisTokenService);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        com.lichun.agsell.common.BaseContext.removeCurrentId();
        com.lichun.agsell.common.AdminContext.removeCurrentAdmin();
    }

    private MockHttpServletRequest buildRequest(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    @DisplayName("普通用户 token 访问管理端接口应被拒绝")
    void userTokenCannotAccessAdminApi() {
        String userToken = jwtUtils.generateToken(100L);
        MockHttpServletRequest request = buildRequest("/api/admin/order/list", userToken);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("管理员 token 访问管理端接口应放行")
    void adminTokenCanAccessAdminApi() {
        String adminToken = jwtUtils.generateAdminToken(1L, "ADMIN");
        MockHttpServletRequest request = buildRequest("/api/admin/order/list", adminToken);

        assertTrue(interceptor.preHandle(request, response, null));
        assertEquals(1L, com.lichun.agsell.common.AdminContext.getCurrentAdminId());
    }

    @Test
    @DisplayName("AI 知识库：ADMIN/SUPER_ADMIN 可写，OPERATOR 按前缀拒绝（运营专员不该改 RAG 的答案来源）")
    void aiKbFollowsTheRoleMatrix() {
        for (String role : new String[]{"ADMIN", "SUPER_ADMIN"}) {
            String token = jwtUtils.generateAdminToken(1L, role);
            MockHttpServletRequest request = buildRequest("/api/admin/ai/kb/faqs", token);
            assertTrue(interceptor.preHandle(request, response, null), role + " 应能维护知识库");
        }

        String operatorToken = jwtUtils.generateAdminToken(2L, "OPERATOR");
        // 这张禁止表是"显式禁止清单"：漏一行就等于默认放行，所以每个新管理模块都要在这儿补断言
        for (String uri : new String[]{"/api/admin/ai/kb/faqs", "/api/admin/ai/kb/stats",
                "/api/admin/ai/kb/products/index"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interceptor.preHandle(buildRequest(uri, operatorToken), new MockHttpServletResponse(), null));
            assertEquals(ErrorCode.ADMIN_NO_AUTH_ERROR.getCode(), ex.getCode(), uri + " 应拒绝运营专员");
        }

        // 反过来钉住"运营该能用的没被误伤"：/api/admin/ai 只挡 AI 模块，不挡商品
        assertTrue(interceptor.preHandle(buildRequest("/api/admin/product/list", operatorToken),
                new MockHttpServletResponse(), null));
    }

    @Test
    @DisplayName("管理员 token 访问用户端接口应被拒绝")
    void adminTokenCannotAccessUserApi() {
        String adminToken = jwtUtils.generateAdminToken(1L, "ADMIN");
        MockHttpServletRequest request = buildRequest("/api/user/info", adminToken);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("普通用户 token 访问用户端接口应放行")
    void userTokenCanAccessUserApi() {
        String userToken = jwtUtils.generateToken(100L);
        MockHttpServletRequest request = buildRequest("/api/user/info", userToken);

        assertTrue(interceptor.preHandle(request, response, null));
        assertEquals(100L, com.lichun.agsell.common.BaseContext.getCurrentId());
    }

    @Test
    @DisplayName("白名单接口无需登录直接放行")
    void whitelistBypassesAuth() {
        MockHttpServletRequest request = buildRequest("/api/admin/login", null);
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("缺少 token 访问受保护接口应提示未登录")
    void missingTokenRejected() {
        MockHttpServletRequest request = buildRequest("/api/order/list", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("未登录不能上传文件（不再匿名放行）")
    void anonymousUploadRejected() {
        MockHttpServletRequest request = buildRequest("/api/file/upload", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("用户 token 可上传文件")
    void userTokenCanUpload() {
        String userToken = jwtUtils.generateToken(100L);
        MockHttpServletRequest request = buildRequest("/api/file/upload", userToken);
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("管理员 token 也可上传文件")
    void adminTokenCanUpload() {
        String adminToken = jwtUtils.generateAdminToken(1L, "ADMIN");
        MockHttpServletRequest request = buildRequest("/api/file/upload", adminToken);
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("文件删除接口仅管理员可用")
    void deleteFileRequiresAdmin() {
        // 普通用户 token 删除文件被拒绝
        String userToken = jwtUtils.generateToken(100L);
        MockHttpServletRequest request = buildRequest("/api/admin/file/delete", userToken);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());

        // 管理员 token 可删除
        String adminToken = jwtUtils.generateAdminToken(1L, "ADMIN");
        MockHttpServletRequest adminRequest = buildRequest("/api/admin/file/delete", adminToken);
        assertTrue(interceptor.preHandle(adminRequest, response, null));
    }
}
