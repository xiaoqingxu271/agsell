package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置实体
 * 对应数据库表 sys_config
 */
@Data
@TableName("sys_config")
public class SysConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
