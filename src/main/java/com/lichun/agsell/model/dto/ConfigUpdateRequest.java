package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 系统配置批量更新请求
 */
@Data
public class ConfigUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置项列表 */
    private List<ConfigItem> items;

    @Data
    public static class ConfigItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 配置键 */
        private String configKey;

        /** 配置值 */
        private String configValue;
    }
}
