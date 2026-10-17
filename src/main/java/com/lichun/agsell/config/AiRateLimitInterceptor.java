package com.lichun.agsell.config;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * AI 端点固定窗口限流。
 * <p>
 * Agnes 是免费额度、Python 服务是单实例，两者全站共享：一个循环脚本几十秒就能把当天
 * 额度烧光并把 AI 客服拖垮。因此在网关层按调用主体设硬上限，超额直接拒绝，不转发给 Python。
 * <p>
 * 计数存 Redis（多实例共享同一桶，且自带原子自增）。Redis 故障时<b>放行</b>：限流只是保护层，
 * 不能让它自己变成新依赖——缓存挂了把 AI 客服和首页推荐一起带走是更严重的故障。
 * <p>
 * 必须注册在 {@link JwtAuthInterceptor} 之后：它已对白名单接口做过"可选登录"，
 * 这里直接取 BaseContext 里的 userId，避免同一请求二次解析 JWT + 二次查 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    /** 窗口长度（秒），同窗口内对齐计数：slot = epochSecond / window */
    @Value("${ai-service.rate-limit.window-seconds}")
    private long windowSeconds;

    /** AI 对话（含流式）每主体每窗口次数上限 */
    @Value("${ai-service.rate-limit.chat-limit}")
    private int chatLimit;

    /** 首页个性化推荐 feed 上限：翻页/下拉刷新会连续触发，阈值要比对话宽 */
    @Value("${ai-service.rate-limit.feed-limit}")
    private int feedLimit;

    /** 浏览足迹上报上限：进详情页即上报，正常浏览远低于此值 */
    @Value("${ai-service.rate-limit.view-limit}")
    private int viewLimit;

    private static final String KEY_PREFIX = "ai:rl:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String scope = scopeOf(request.getServletPath());
        if (scope == null) {
            return true;
        }
        int limit = switch (scope) {
            case "chat" -> chatLimit;
            case "feed" -> feedLimit;
            default -> viewLimit;
        };
        String key = KEY_PREFIX + scope + ":" + (System.currentTimeMillis() / 1000 / windowSeconds)
                + ":" + principal(request);
        long count;
        try {
            Long incremented = redisTemplate.opsForValue().increment(key);
            count = incremented == null ? 0L : incremented;
            // 仅首笔设置 TTL，否则窗口 key 永不过期、Redis 无限增长
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
        } catch (Exception e) {
            log.warn("AI 限流计数失败，放行本次请求: scope={}, err={}", scope, e.getMessage());
            return true;
        }
        if (count > limit) {
            log.warn("AI 限流命中: key={}, count={}, limit={}", key, count, limit);
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
        return true;
    }

    /** 受控路径 → 限流桶；未命中返回 null 放行 */
    private static String scopeOf(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/ai/chat") || path.startsWith("/ai/chat/")) {
            return "chat";
        }
        if (path.startsWith("/product/recommend/feed")) {
            return "feed";
        }
        if (path.startsWith("/product/view")) {
            return "view";
        }
        return null;
    }

    /**
     * 计数主体：登录用户用 JWT 解析出的 userId（客户端改不动），游客退化为客户端 IP。
     * <p>
     * IP 取 X-Forwarded-For 末位——那是我方 nginx 追加的、客户端伪造不了的一段；
     * 本地直连无代理链时用 remoteAddr。游客桶可被伪造前置条目绕过，真正兜住额度的是按 userId 的上限。
     */
    private static String principal(HttpServletRequest request) {
        Long userId = BaseContext.getCurrentId();
        if (userId != null) {
            return "u" + userId;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) {
                return "ip" + last;
            }
        }
        return "ip" + request.getRemoteAddr();
    }
}
