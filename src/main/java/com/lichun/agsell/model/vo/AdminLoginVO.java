package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员登录响应
 */
@Data
public class AdminLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** JWT Token */
    private String token;

    /** 管理员ID */
    private Long adminId;

    /** 真实姓名 */
    private String realName;

    /** 角色 */
    private String role;
}
