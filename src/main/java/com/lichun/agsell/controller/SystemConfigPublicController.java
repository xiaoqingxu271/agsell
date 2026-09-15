package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.ConfigItemVO;
import com.lichun.agsell.service.SysConfigService;
import com.lichun.agsell.utils.ResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置（公开读取，小程序端使用）
 * GET /api/system/config?keys=service_phone,platform_name
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SystemConfigPublicController {

    private final SysConfigService sysConfigService;

    /**
     * 查询配置，keys 为空返回全部
     *
     * @param keys 逗号分隔的配置键，可空
     */
    @GetMapping
    public BaseResponse<Map<String, String>> getConfigs(
            @RequestParam(required = false) String keys) {
        List<String> keyList = (keys == null || keys.isBlank())
                ? List.of()
                : Arrays.asList(keys.split(","));
        Map<String, String> result = new LinkedHashMap<>();
        for (ConfigItemVO item : sysConfigService.listConfigs()) {
            if (keyList.isEmpty() || keyList.contains(item.getConfigKey())) {
                result.put(item.getConfigKey(), item.getConfigValue());
            }
        }
        return ResultUtils.success(result);
    }
}
