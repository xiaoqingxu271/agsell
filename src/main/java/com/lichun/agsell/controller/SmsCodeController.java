package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.service.SmsCodeService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "短信验证码接口", description = "发送短信验证码")
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsCodeController {

    private final SmsCodeService smsCodeService;
    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀 */
    private static final String CODE_KEY_PREFIX = "sms:code:";

    @Operation(summary = "发送短信验证码")
    @PostMapping("/send")
    public BaseResponse<Map<String, Object>> sendCode(@RequestParam String phone) {
        smsCodeService.sendCode(phone);
        // TODO: 生产环境移除 code 返回
        String code = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone);
        return ResultUtils.success(Map.of(
                "message", "验证码已发送",
                "code", code
        ));
    }
}
