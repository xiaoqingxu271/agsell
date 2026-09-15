package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端-管理员列表响应项（不含密码）
 */
@Data
public class AdminListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    /** 角色 SUPER_ADMIN / ADMIN / OPERATOR */
    private String role;

    /** 状态 0=禁用 1=正常 */
    private Integer status;

    private String loginIp;

    private LocalDateTime loginTime;

    private LocalDateTime createTime;
}
