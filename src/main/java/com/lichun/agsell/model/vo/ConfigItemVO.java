package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统配置项响应
 */
@Data
public class ConfigItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String configKey;

    private String configValue;

    private String description;
}
