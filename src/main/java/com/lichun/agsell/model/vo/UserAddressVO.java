package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址响应
 */
@Data
public class UserAddressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    /** 完整地址 */
    private String fullAddress;

    /** 是否默认地址 */
    private Integer isDefault;

    private String tag;

    private LocalDateTime createTime;
}
