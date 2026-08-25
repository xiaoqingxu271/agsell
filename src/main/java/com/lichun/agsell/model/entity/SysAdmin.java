package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员实体
 * 对应数据库表 sys_admin
 */
@Data
@TableName("sys_admin")
public class SysAdmin implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 加密密码（BCrypt） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色 ADMIN / OPERATOR */
    private String role;

    /** 状态 0=禁用 1=正常 */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
