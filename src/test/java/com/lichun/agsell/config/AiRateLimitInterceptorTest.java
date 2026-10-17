package com.lichun.agsell.config;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AI 端点限流边界：阈值命中即拒、按主体/接口分桶、窗口 TTL、Redis 故障放行
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AiRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        interceptor = new AiRateLimitInterceptor(redisTemplate);
        ReflectionTestUtils.setField(interceptor, "windowSeconds", 60L);
        ReflectionTestUtils.setField(interceptor, "chatLimit", 2);
        ReflectionTestUtils.setField(interceptor, "feedLimit", 5);
        ReflectionTestUtils.setField(interceptor, "viewLimit", 9);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    /** 按 key 各自累加的 increment，贴近 Redis 原子自增语义 */
    private void fakeRedisCounters() {
        Map<String, Long> counters = new HashMap<>();
        when(valueOps.increment(anyString()))
                .thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
    }

    private MockHttpServletRequest request(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api" + servletPath);
        request.setServletPath(servletPath);
        request.setRemoteAddr("10.0.0.9");
        return request;
    }

    private boolean preHandle(MockHttpServletRequest request) {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    /** 最近一次计数用到的 Redis key */
    private String lastKey() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, atLeastOnce()).increment(captor.capture());
        List<String> keys = captor.getAllValues();
        return keys.get(keys.size() - 1);
    }

    // ---------- 阈值 ----------

    @Test
    @DisplayName("未超阈值放行，且首笔计数设置窗口 TTL")
    void underLimit_passes_andSetsTtlOnce() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertTrue(preHandle(request("/ai/chat")));

        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("第 3 次对话超过 chat 阈值：抛 42900，非首笔不再续期 TTL")
    void overLimit_throws42900() {
        fakeRedisCounters();
        assertTrue(preHandle(request("/ai/chat")));
        assertTrue(preHandle(request("/ai/chat")));

        BusinessException e = assertThrows(BusinessException.class, () -> preHandle(request("/ai/chat")));
        assertEquals(ErrorCode.AI_RATE_LIMITED.getCode(), e.getCode());
        // 只在首笔续期，否则窗口被反复请求无限延长
        verify(redisTemplate, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("流式对话与同步对话共用 chat 桶：/ai/chat/stream 第 3 次被拒")
    void streamSharesChatBucket() {
        fakeRedisCounters();
        assertTrue(preHandle(request("/ai/chat")));
        assertTrue(preHandle(request("/ai/chat/stream")));

        assertThrows(BusinessException.class, () -> preHandle(request("/ai/chat/stream")));
        assertTrue(lastKey().startsWith("ai:rl:chat:"));
    }

    @Test
    @DisplayName("feed/view 各有独立桶与阈值：对话被限流不影响首页推荐与浏览上报")
    void otherScopesHaveOwnBuckets() {
        fakeRedisCounters();
        preHandle(request("/ai/chat"));
        preHandle(request("/ai/chat"));
        assertThrows(BusinessException.class, () -> preHandle(request("/ai/chat")));

        assertTrue(preHandle(request("/product/recommend/feed")));
        assertTrue(lastKey().startsWith("ai:rl:feed:"));
        assertTrue(preHandle(request("/product/view")));
        assertTrue(lastKey().startsWith("ai:rl:view:"));
    }

    // ---------- 计数主体 ----------

    @Test
    @DisplayName("登录用户按 userId 分桶：A 刷爆额度不波及 B")
    void loggedInUsersGetSeparateBuckets() {
        fakeRedisCounters();
        BaseContext.setCurrentId(100L);
        preHandle(request("/ai/chat"));
        preHandle(request("/ai/chat"));
        assertThrows(BusinessException.class, () -> preHandle(request("/ai/chat")));
        assertTrue(lastKey().endsWith(":u100"));

        BaseContext.setCurrentId(200L);
        assertTrue(preHandle(request("/ai/chat")));
        assertTrue(lastKey().endsWith(":u200"));
    }

    @Test
    @DisplayName("游客按客户端 IP 分桶：取 X-Forwarded-For 末位（nginx 追加段），伪造前置条目换不了桶")
    void guestKeyedByForwardedForLastHop() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        MockHttpServletRequest request = request("/ai/chat");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");

        assertTrue(preHandle(request));
        assertTrue(lastKey().endsWith(":ip5.6.7.8"));
    }

    @Test
    @DisplayName("游客无代理链时退回直连地址")
    void guestFallsBackToRemoteAddr() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertTrue(preHandle(request("/ai/chat")));
        assertTrue(lastKey().endsWith(":ip10.0.0.9"));
    }

    // ---------- 降级与放行 ----------

    @Test
    @DisplayName("Redis 不可用时放行：限流层故障不得带走 AI 功能")
    void redisFailureFailsOpen() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Unable to connect to Redis"));

        assertTrue(preHandle(request("/ai/chat")));
        assertTrue(preHandle(request("/product/recommend/feed")));
    }

    @Test
    @DisplayName("非受控路径不计数（商品列表、内部回调接口正常放行）")
    void uncontrolledPathsNotCounted() {
        assertTrue(preHandle(request("/product/list")));
        assertTrue(preHandle(request("/ai/internal/products")));
        verify(valueOps, never()).increment(anyString());
    }
}
