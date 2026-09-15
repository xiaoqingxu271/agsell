package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 * 对应数据库表 sys_log（只追加：insert + select）
 */
@Data
@TableName("sys_log")
public class SysLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人ID */
    private Long adminId;

    /** 操作人姓名 */
    private String adminName;

    /** 操作模块 */
    private String module;

    /** 操作动作 */
    private String action;

    /** 操作内容描述 */
    private String content;

    /** 操作IP */
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
