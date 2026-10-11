package com.lichun.agsell.controller.user;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.service.SmsCodeService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "短信验证码接口", description = "发送短信验证码")
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsCodeController {

    private final SmsCodeService smsCodeService;
    private final StringRedisTemplate redisTemplate;

    @Value("${sms.echo-code:false}")
    private boolean echoCode;

    /** Redis key 前缀 */
    private static final String CODE_KEY_PREFIX = "sms:code:";

    @Operation(summary = "发送短信验证码")
    @PostMapping("/send")
    public BaseResponse<Map<String, Object>> sendCode(@RequestParam String phone) {
        smsCodeService.sendCode(phone);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "验证码已发送");
        // 短信通道未接入，开发/演示环境把验证码回传前端自动填充；生产环境开关关闭
        if (echoCode) {
            data.put("code", redisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone));
        }
        return ResultUtils.success(data);
    }
}
