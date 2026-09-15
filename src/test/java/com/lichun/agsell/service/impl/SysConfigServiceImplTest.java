package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysConfigMapper;
import com.lichun.agsell.model.dto.ConfigUpdateRequest;
import com.lichun.agsell.model.entity.SysConfig;
import com.lichun.agsell.model.vo.ConfigItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 系统配置边界测试：默认值兜底、白名单键校验、新增/更新分支
 */
@ExtendWith(MockitoExtension.class)
class SysConfigServiceImplTest {

    @Mock
    private SysConfigMapper configMapper;

    @InjectMocks
    private SysConfigServiceImpl sysConfigService;

    @Test
    @DisplayName("读取：配置不存在时返回默认值，空键返回默认值")
    void getConfigDefaultFallback() {
        when(configMapper.selectOne(any())).thenReturn(null);
        assertEquals("0", sysConfigService.getConfigOrDefault("default_freight", "0"));
        assertEquals("农产品销售系统", sysConfigService.getConfigOrDefault("platform_name", "x"));
        assertEquals("兜底", sysConfigService.getConfigOrDefault("unknown_key", "兜底"));
        assertEquals("兜底", sysConfigService.getConfigOrDefault(null, "兜底"));
    }

    @Test
    @DisplayName("读取：配置存在时返回配置值")
    void getConfigExists() {
        SysConfig cfg = new SysConfig();
        cfg.setConfigKey("service_phone");
        cfg.setConfigValue("400-123-4567");
        when(configMapper.selectOne(any())).thenReturn(cfg);
        assertEquals("400-123-4567", sysConfigService.getConfig("service_phone"));
    }

    @Test
    @DisplayName("更新：空 items / 空配置键 / 非法配置键均拒绝")
    void updateRejectsInvalid() {
        ConfigUpdateRequest req = new ConfigUpdateRequest();
        assertThrows(BusinessException.class, () -> sysConfigService.updateConfigs(req));

        ConfigUpdateRequest.ConfigItem item = new ConfigUpdateRequest.ConfigItem();
        req.setItems(List.of(item));
        assertThrows(BusinessException.class, () -> sysConfigService.updateConfigs(req));

        item.setConfigKey("evil_key");
        item.setConfigValue("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> sysConfigService.updateConfigs(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("非法配置键"));
        verifyNoInteractions(configMapper);
    }

    @Test
    @DisplayName("更新：合法键不存在时新增，存在时更新")
    void updateInsertAndUpdateBranch() {
        ConfigUpdateRequest req = new ConfigUpdateRequest();
        ConfigUpdateRequest.ConfigItem item = new ConfigUpdateRequest.ConfigItem();
        item.setConfigKey("default_freight");
        item.setConfigValue("8");
        req.setItems(List.of(item));

        // 不存在 → insert
        when(configMapper.selectOne(any())).thenReturn(null);
        sysConfigService.updateConfigs(req);
        ArgumentCaptor<SysConfig> insertCaptor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).insert(insertCaptor.capture());
        assertEquals("8", insertCaptor.getValue().getConfigValue());

        // 存在 → update（保留 id）
        clearInvocations(configMapper);
        SysConfig exist = new SysConfig();
        exist.setId(100L);
        exist.setConfigKey("default_freight");
        exist.setConfigValue("0");
        when(configMapper.selectOne(any())).thenReturn(exist);
        sysConfigService.updateConfigs(req);
        ArgumentCaptor<SysConfig> updateCaptor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).updateById(updateCaptor.capture());
        assertEquals(100L, updateCaptor.getValue().getId());
        assertEquals("8", updateCaptor.getValue().getConfigValue());
    }

    @Test
    @DisplayName("列表：按 id 升序返回全部配置项")
    void listConfigsOrdered() {
        SysConfig a = new SysConfig();
        a.setConfigKey("platform_name");
        a.setConfigValue("农产品销售系统");
        a.setDescription("平台名称");
        when(configMapper.selectList(any())).thenReturn(List.of(a));

        List<ConfigItemVO> list = sysConfigService.listConfigs();
        assertEquals(1, list.size());
        assertEquals("platform_name", list.get(0).getConfigKey());
        assertEquals("农产品销售系统", list.get(0).getConfigValue());
        assertEquals("平台名称", list.get(0).getDescription());
    }
}
