package com.lichun.agsell.service.impl;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 短信验证码边界测试：格式校验、发送频控、过期、错误码、一次性使用
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsCodeServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SmsCodeServiceImpl smsCodeService;

    private static final String PHONE = "13800138000";
    private static final String CODE_KEY = "sms:code:" + PHONE;
    private static final String SEND_KEY = "sms:send:" + PHONE;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        smsCodeService = new SmsCodeServiceImpl(redisTemplate);
    }

    // ---------- sendCode ----------

    @Test
    @DisplayName("发送：手机号为空 / null 拒绝")
    void send_nullOrBlankPhone_throws() {
        BusinessException e1 = assertThrows(BusinessException.class, () -> smsCodeService.sendCode(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e1.getCode());
        assertThrows(BusinessException.class, () -> smsCodeService.sendCode("  "));
    }

    @Test
    @DisplayName("发送：非法手机号格式拒绝（字母/位数不足/座机）")
    void send_invalidPhoneFormat_throws() {
        for (String bad : new String[]{"1380013800", "138001380001", "abc12345678", "12345678901", "21800138000"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> smsCodeService.sendCode(bad),
                    "应拒绝: " + bad);
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        }
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("发送：60 秒频控内重复发送被拒绝，并提示剩余秒数")
    void send_rateLimited_throws() {
        when(redisTemplate.getExpire(SEND_KEY, TimeUnit.SECONDS)).thenReturn(42L);

        BusinessException e = assertThrows(BusinessException.class, () -> smsCodeService.sendCode(PHONE));
        assertTrue(e.getMessage().contains("42"));
        // 未写入验证码
        verify(valueOps, never()).set(eq(CODE_KEY), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("发送：正常发送写入 6 位验证码（5分钟有效）+ 发送间隔标记（60秒有效）")
    void send_ok_setsCodeAndInterval() {
        when(redisTemplate.getExpire(SEND_KEY, TimeUnit.SECONDS)).thenReturn(-2L); // key 不存在

        smsCodeService.sendCode(PHONE);

        verify(valueOps).set(eq(CODE_KEY), matches("\\d{6}"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOps).set(eq(SEND_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
    }

    // ---------- verifyCode ----------

    @Test
    @DisplayName("校验：参数为 null 返回 false（不抛异常）")
    void verify_nullArgs_false() {
        assertFalse(smsCodeService.verifyCode(null, "123456"));
        assertFalse(smsCodeService.verifyCode(PHONE, null));
    }

    @Test
    @DisplayName("校验：验证码不存在/已过期返回 false")
    void verify_expired_false() {
        when(valueOps.get(CODE_KEY)).thenReturn(null);
        assertFalse(smsCodeService.verifyCode(PHONE, "123456"));
    }

    @Test
    @DisplayName("校验：验证码错误返回 false 且不删除（允许重试）")
    void verify_wrongCode_false_andKept() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        assertFalse(smsCodeService.verifyCode(PHONE, "654321"));
        verify(redisTemplate, never()).delete(CODE_KEY);
    }

    @Test
    @DisplayName("校验：正确验证码通过并立即删除（一次性使用）")
    void verify_correct_true_andDeleted() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        assertTrue(smsCodeService.verifyCode(PHONE, "123456"));
        verify(redisTemplate).delete(CODE_KEY);
    }

    @Test
    @DisplayName("校验：验证码使用后重放（第二次）返回 false")
    void verify_replay_false() {
        // 第一次：存在且正确 → 删除
        when(valueOps.get(CODE_KEY)).thenReturn("123456").thenReturn(null);
        assertTrue(smsCodeService.verifyCode(PHONE, "123456"));
        // 第二次重放同一验证码：已删除 → false
        assertFalse(smsCodeService.verifyCode(PHONE, "123456"));
    }

    @Test
    @DisplayName("边界：有效期常量正确（验证码 300 秒 / 频控 60 秒）")
    void ttlConstants() throws Exception {
        // 通过反射锁定关键常量，防止无意改动影响安全语义
        assertEquals(300L, field(SmsCodeServiceImpl.class, "CODE_EXPIRE_SECONDS"));
        assertEquals(60, field(SmsCodeServiceImpl.class, "SEND_INTERVAL_SECONDS"));
        assertEquals(6, field(SmsCodeServiceImpl.class, "CODE_LENGTH"));
    }

    private static Object field(Class<?> clazz, String name) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
