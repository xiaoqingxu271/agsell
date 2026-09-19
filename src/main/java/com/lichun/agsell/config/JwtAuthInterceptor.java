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
            // AI 客服对话（游客可咨询 FAQ；登录后业务查询 userId 由 Controller 解析 JWT）
            "/api/ai/chat",
            // AI 内部接口（Python 服务回调，由 AiInternalAuthInterceptor 校验 X-Internal-Key）
            "/api/ai/internal",
            // 商品浏览接口（无需登录）
            "/api/product/category/list",
            "/api/product/detail",
            "/api/product/list",
            "/api/product/hot",
            "/api/product/new",
            // 搜索接口（无需登录）
            "/api/product/search",
            "/api/product/search/hot",
            // 评价浏览接口（无需登录）
            "/api/review/product",
            // 轮播图接口（无需登录）
            "/api/banner/list",
            // 溯源查询接口（无需登录）
            "/api/trace",
            // 秒杀活动浏览接口（无需登录；下单 /api/seckill/order 需登录）
            "/api/seckill/list",
            "/api/seckill/detail",
            // 领券中心浏览接口（无需登录；领取/我的券包/可用券需登录）
            "/api/coupon/list",
            // 系统配置公开读取（小程序端）
            "/api/system/config",
            // Swagger/Knife4j 文档
            "/api/doc.html",
            "/api/v3/api-docs",
            "/api/swagger-ui",
            "/api/swagger-resources",
            "/api/webjars"
    };

    /** OPERATOR 角色禁止访问的业务管理前缀（需求权限矩阵：运营专员仅商品/订单/分类/统计） */
    private static final String[] OPERATOR_FORBIDDEN_PREFIXES = {
            "/api/admin/user",
            "/api/admin/review",
            "/api/admin/banner",
            "/api/admin/after-sales",
            "/api/admin/trace",
            "/api/admin/seckill",
            "/api/admin/coupon",
            "/api/admin/hot-word"
    };

    /** 判断 uri 是否命中运营专员禁止访问的模块 */
    private boolean isOperatorForbidden(String uri) {
        for (String prefix : OPERATOR_FORBIDDEN_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 去除查询参数，只保留路径部分用于白名单匹配
        String uri = request.getRequestURI();
        int queryStart = uri.indexOf('?');
        if (queryStart > 0) {
            uri = uri.substring(0, queryStart);
        }

        // 白名单直接放行（但做"可选登录"：带了有效用户 token 时仍解析并设置上下文，
        // 供领券中心 received 标记等匿名可浏览接口使用；无 token/游客保持匿名）
        for (String path : WHITE_LIST) {
            if (uri.equals(path) || uri.startsWith(path + "/")) {
                fillUserContextIfPresent(request);
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
            boolean isSystemApi = uri.startsWith("/api/admin/system/");
            boolean isFileUploadApi = uri.startsWith("/api/file/upload");
            if (isAdminApi || (isFileUploadApi && "admin".equals(type))) {
                if (!"admin".equals(type)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无管理员权限");
                }
                Long adminId = userId;
                String role = claims.get("role", String.class);
                // 系统管理接口（管理员管理/系统配置/操作日志）仅超级管理员可访问
                if (isSystemApi && !"SUPER_ADMIN".equals(role)) {
                    throw new BusinessException(ErrorCode.ADMIN_NO_AUTH_ERROR, "仅超级管理员可操作系统管理");
                }
                // 运营专员（OPERATOR）仅可访问商品/订单/分类/统计，其余业务管理模块拒绝（需求权限矩阵）
                if ("OPERATOR".equals(role) && isOperatorForbidden(uri)) {
                    throw new BusinessException(ErrorCode.ADMIN_NO_AUTH_ERROR, "运营专员无该模块权限");
                }
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

    /**
     * 白名单接口的"可选登录"：若请求携带了有效用户 token，则解析并设置用户上下文；
     * 游客（无 token / 无效 token / 管理端 token）保持匿名，不抛错。
     */
    private void fillUserContextIfPresent(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        try {
            var claims = jwtUtils.parseToken(token);
            String type = claims.get("type", String.class);
            if ("admin".equals(type)) {
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            String jti = claims.get("jti", String.class);
            if (redisTokenService.validateUserToken(userId, token, jti)) {
                BaseContext.setCurrentId(userId, jti);
            }
        } catch (Exception ignored) {
            // 无效/过期 token 按游客处理
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
        AdminContext.removeCurrentAdmin();
    }
}
