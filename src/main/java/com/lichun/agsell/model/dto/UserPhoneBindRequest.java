package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 手机号绑定请求
 */
@Data
public class UserPhoneBindRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 手机号 */
    private String phone;

    /** 短信验证码 */
    private String code;
}
