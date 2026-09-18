package com.lichun.agsell.config;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AI 内部接口鉴权拦截器
 * 供 Python AI 客服服务回调（携带 X-Internal-Key），与用户 JWT 体系隔离。
 */
@Slf4j
@Component
public class AiInternalAuthInterceptor implements HandlerInterceptor {

    @Value("${ai-service.internal-key:}")
    private String internalKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getHeader("X-Internal-Key");
        if (internalKey == null || internalKey.isEmpty() || !internalKey.equals(key)) {
            log.warn("AI 内部接口鉴权失败，来源 IP: {}", request.getRemoteAddr());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "内部接口鉴权失败");
        }
        return true;
    }
}
