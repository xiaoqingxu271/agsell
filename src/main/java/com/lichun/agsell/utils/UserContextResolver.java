package com.lichun.agsell.utils;

import com.lichun.agsell.service.RedisTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户上下文解析：从请求 JWT 中取 userId（游客/无效 token 返回 null）。
 * <p>
 * 供 AI 相关白名单接口（自行解析 token、不走 JwtAuthInterceptor）统一使用，
 * 保证与 JwtAuthInterceptor 同口径校验 Redis 会话：已登出/被顶号的旧 token 不得继续驱动 AI。
 */
@Component
@RequiredArgsConstructor
public class UserContextResolver {

    private final JwtUtils jwtUtils;
    private final RedisTokenService redisTokenService;

    public Long resolveUserId(HttpServletRequest request) {
        try {
            String token = jwtUtils.getTokenFromRequest(request);
            if (token == null) {
                return null;
            }
            Claims claims = jwtUtils.parseToken(token);
            String type = claims.get("type", String.class);
            if (!"user".equals(type)) {
                return null;
            }
            Long userId = Long.valueOf(claims.getSubject());
            String jti = claims.get("jti", String.class);
            if (!redisTokenService.validateUserToken(userId, token, jti)) {
                return null;
            }
            return userId;
        } catch (Exception e) {
            // token 无效视为游客，不阻断游客可用的功能
            return null;
        }
    }
}
