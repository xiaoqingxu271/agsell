package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑管理员请求（允许修改用户名 / 姓名 / 角色）
 */
@Data
public class AdminUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名（可选，为空则不修改；需唯一） */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色：仅允许 ADMIN / OPERATOR，且不高于当前操作者角色 */
    private String role;
}
