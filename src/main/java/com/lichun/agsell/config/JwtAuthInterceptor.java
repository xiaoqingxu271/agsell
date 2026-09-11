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
            "/api/product/list",
            "/api/product/hot",
            "/api/product/new",
            // 评价浏览接口（无需登录）
            "/api/review/product",
            // 轮播图接口（无需登录）
            "/api/banner/list",
            // 溯源查询接口（无需登录）
            "/api/trace",
            // Swagger/Knife4j 文档
            "/api/doc.html",
            "/api/v3/api-docs",
            "/api/swagger-ui",
            "/api/swagger-resources",
            "/api/webjars"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 去除查询参数，只保留路径部分用于白名单匹配
        String uri = request.getRequestURI();
        int queryStart = uri.indexOf('?');
        if (queryStart > 0) {
            uri = uri.substring(0, queryStart);
        }

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

            // 管理端接口必须使用管理员 token，防止普通用户越权访问；
            // 文件上传接口（/api/file/upload）管理端与用户端共用，两端 token 均可
            boolean isAdminApi = uri.startsWith("/api/admin/");
            boolean isFileUploadApi = uri.startsWith("/api/file/upload");
            if (isAdminApi || (isFileUploadApi && "admin".equals(type))) {
                if (!"admin".equals(type)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无管理员权限");
                }
                Long adminId = userId;
                String role = claims.get("role", String.class);
                // 校验 Redis 中的 token 是否一致
                if (!redisTokenService.validateAdminToken(adminId, token, jti)) {
                    throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录");
                }
                AdminContext.setCurrentAdmin(adminId, role, jti);
            } else {
                // 用户端接口必须使用用户 token
                if ("admin".equals(type)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "请使用用户账号访问");
                }
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
