package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑管理员请求（仅允许修改姓名 / 角色）
 */
@Data
public class AdminUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 真实姓名 */
    private String realName;

    /** 角色：仅允许 ADMIN / OPERATOR，且不高于当前操作者角色 */
    private String role;
}
