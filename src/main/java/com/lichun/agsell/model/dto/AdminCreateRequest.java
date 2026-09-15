package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增管理员请求
 */
@Data
public class AdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名 */
    private String username;

    /** 密码（≥ 6 位） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：仅允许 ADMIN / OPERATOR */
    private String role;
}
