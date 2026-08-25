package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 手机号 */
    private String phone;

    /** 验证码 */
    private String code;

    /** 昵称 */
    private String nickname;

    /** 密码 */
    private String password;
}
