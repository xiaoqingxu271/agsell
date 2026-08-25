package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员信息响应
 */
@Data
public class AdminInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long adminId;

    private String realName;

    private String role;
}
