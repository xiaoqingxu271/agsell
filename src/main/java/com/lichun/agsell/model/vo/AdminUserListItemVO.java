package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端用户列表响应项
 */
@Data
public class AdminUserListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String phone;

    /** 状态 0=禁用 1=正常 */
    private Integer status;

    private LocalDateTime loginTime;

    private LocalDateTime createTime;
}
