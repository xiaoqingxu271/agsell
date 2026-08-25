package com.lichun.agsell.service.impl;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.SmsCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeServiceImpl implements SmsCodeService {

    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀 */
    private static final String CODE_KEY_PREFIX = "sms:code:";

    /** 验证码有效期（秒） */
    private static final long CODE_EXPIRE_SECONDS = 300;

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 发送间隔（秒）- 防止频繁发送 */
    private static final int SEND_INTERVAL_SECONDS = 60;

    /** 发送间隔 key 前缀 */
    private static final String SEND_INTERVAL_KEY_PREFIX = "sms:send:";

    @Override
    public void sendCode(String phone) {
        // 1. 手机号格式校验
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号不能为空");
        }
        // TODO: 接入真实短信服务（如阿里云短信、腾讯云短信）
        // 开发阶段直接生成6位随机验证码，后端返回给前端

        // 2. 检查发送间隔
        String sendKey = SEND_INTERVAL_KEY_PREFIX + phone;
        Long remaining = redisTemplate.getExpire(sendKey, TimeUnit.SECONDS);
        if (remaining != null && remaining > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "验证码已在发送中，请" + remaining + "秒后再试");
        }

        // 3. 生成6位随机验证码
        String code = generateCode();
        log.info("[SendSmsCode] 生成验证码, phone={}, code={}", phone, code);

        // 4. 存入 Redis，设置5分钟过期
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + phone, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 5. 记录发送时间，用于限制发送频率
        redisTemplate.opsForValue().set(sendKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // TODO: 生产环境调用短信服务商 API 发送
        // log.info("[SendSmsCode] 短信发送成功, phone={}", phone);
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        String redisKey = CODE_KEY_PREFIX + phone;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            log.warn("[VerifyCode] 验证码不存在或已过期, phone={}", phone);
            return false;
        }

        if (!storedCode.equals(code)) {
            log.warn("[VerifyCode] 验证码错误, phone={}", phone);
            return false;
        }

        // 校验成功后删除，确保一次性使用
        redisTemplate.delete(redisKey);
        log.info("[VerifyCode] 验证码校验成功, phone={}", phone);
        return true;
    }

    /**
     * 生成指定长度的纯数字验证码
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
