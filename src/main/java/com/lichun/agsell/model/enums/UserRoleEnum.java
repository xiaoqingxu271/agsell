package com.lichun.agsell.model.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {

    ADMIN("ADMIN", "后台管理员"),
    OPERATOR("OPERATOR", "后台运营"),
    USER("USER", "普通用户");

    private final String code;
    private final String desc;

    UserRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
