package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.ConfigUpdateRequest;
import com.lichun.agsell.model.vo.ConfigItemVO;

import java.util.List;

/**
 * 系统配置服务
 */
public interface SysConfigService {

    /** 全部配置项 */
    List<ConfigItemVO> listConfigs();

    /** 批量更新（白名单键校验） */
    void updateConfigs(ConfigUpdateRequest request);

    /** 读取配置值，不存在返回空串 */
    String getConfig(String key);

    /** 读取配置值，不存在返回默认值 */
    String getConfigOrDefault(String key, String defaultValue);
}
