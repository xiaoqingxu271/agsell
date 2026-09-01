package com.lichun.agsell.config;

import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.utils.JwtUtils;
import com.lichun.agsell.service.RedisTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final RedisTokenService redisTokenService;

    /** 无需登录即可访问的接口白名单 */
    private static final String[] WHITE_LIST = {
            "/api/user/login",
            "/api/user/register",
            // /api/user/logout 和 /api/admin/logout 需要认证，不在白名单
            "/api/admin/login",
            "/api/health",
            "/api/sms/send",
            // 商品浏览接口（无需登录）
            "/api/product/category/list",
            "/api/product/detail",
            // Swagger/Knife4j 文档
            "/api/doc.html",
            "/api/v3/api-docs",
            "/api/swagger-ui",
            "/api/swagger-resources",
            "/api/webjars"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        // 白名单直接放行
        for (String path : WHITE_LIST) {
            if (uri.equals(path) || uri.startsWith(path + "/")) {
                log.debug("URI {} matches whitelist path {}", uri, path);
                return true;
            }
        }

        // 提取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        String token = authHeader.substring(7);
        try {
            var claims = jwtUtils.parseToken(token);
            String type = claims.get("type", String.class);
            String jti = claims.get("jti", String.class);
            Long userId = Long.valueOf(claims.getSubject());

            if ("admin".equals(type)) {
                Long adminId = userId;
                String role = claims.get("role", String.class);
                // 校验 Redis 中的 token 是否一致
                if (!redisTokenService.validateAdminToken(adminId, token, jti)) {
                    throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录");
                }
                AdminContext.setCurrentAdmin(adminId, role, jti);
            } else {
                // 校验 Redis 中的 token 是否一致
                if (!redisTokenService.validateUserToken(userId, token, jti)) {
                    throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录");
                }
                BaseContext.setCurrentId(userId, jti);
            }
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
        AdminContext.removeCurrentAdmin();
    }
}
