package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户登录响应
 */
@Data
public class UserLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;
}
