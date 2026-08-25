package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收货地址请求（新增/更新共用）
 */
@Data
public class UserAddressDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 地址ID（更新时必填） */
    private Long id;

    /** 收件人姓名 */
    private String receiver;

    /** 手机号 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认地址 */
    private Integer isDefault;

    /** 标签：家/公司/学校 */
    private String tag;
}
