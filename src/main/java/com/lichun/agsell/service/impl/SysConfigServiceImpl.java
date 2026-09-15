package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysConfigMapper;
import com.lichun.agsell.model.dto.ConfigUpdateRequest;
import com.lichun.agsell.model.entity.SysConfig;
import com.lichun.agsell.model.vo.ConfigItemVO;
import com.lichun.agsell.service.SysConfigService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统配置实现（KV 结构，数据量小不缓存，白名单键防注入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper configMapper;

    /** 合法配置键白名单 */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "platform_name", "service_phone", "default_freight", "free_shipping_threshold");

    /** 内置默认值（配置缺失时兜底） */
    private static final Map<String, String> DEFAULT_VALUES = Map.of(
            "platform_name", "农产品销售系统",
            "service_phone", "",
            "default_freight", "0",
            "free_shipping_threshold", "0");

    @Override
    public List<ConfigItemVO> listConfigs() {
        List<SysConfig> configs = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getId));
        return configs.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public void updateConfigs(ConfigUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getItems() == null || request.getItems().isEmpty(),
                ErrorCode.PARAMS_ERROR, "配置项不能为空");
        for (ConfigUpdateRequest.ConfigItem item : request.getItems()) {
            ThrowUtils.throwIf(item == null || !StringUtils.hasText(item.getConfigKey()),
                    ErrorCode.PARAMS_ERROR, "配置键不能为空");
            ThrowUtils.throwIf(!ALLOWED_KEYS.contains(item.getConfigKey()),
                    ErrorCode.PARAMS_ERROR, "非法配置键: " + item.getConfigKey());

            SysConfig exist = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, item.getConfigKey()));
            if (exist == null) {
                SysConfig cfg = new SysConfig();
                cfg.setConfigKey(item.getConfigKey());
                cfg.setConfigValue(item.getConfigValue());
                configMapper.insert(cfg);
            } else {
                SysConfig update = new SysConfig();
                update.setId(exist.getId());
                update.setConfigValue(item.getConfigValue());
                configMapper.updateById(update);
            }
        }
        log.info("[SysConfig] 更新配置, items={}", request.getItems().size());
    }

    @Override
    public String getConfig(String key) {
        return getConfigOrDefault(key, "");
    }

    @Override
    public String getConfigOrDefault(String key, String defaultValue) {
        if (!StringUtils.hasText(key)) {
            return defaultValue;
        }
        SysConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (cfg != null && cfg.getConfigValue() != null) {
            return cfg.getConfigValue();
        }
        return DEFAULT_VALUES.getOrDefault(key, defaultValue);
    }

    private ConfigItemVO toVO(SysConfig cfg) {
        ConfigItemVO vo = new ConfigItemVO();
        vo.setConfigKey(cfg.getConfigKey());
        vo.setConfigValue(cfg.getConfigValue());
        vo.setDescription(cfg.getDescription());
        return vo;
    }
}
