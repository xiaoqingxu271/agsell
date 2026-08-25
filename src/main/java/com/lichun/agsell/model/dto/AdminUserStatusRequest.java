package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员禁用/启用用户请求
 */
@Data
public class AdminUserStatusRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态 0=禁用 1=启用 */
    private Integer status;
}
