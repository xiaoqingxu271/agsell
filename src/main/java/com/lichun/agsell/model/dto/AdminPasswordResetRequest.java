package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重置管理员密码请求
 */
@Data
public class AdminPasswordResetRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新密码（≥ 6 位） */
    private String newPassword;
}
