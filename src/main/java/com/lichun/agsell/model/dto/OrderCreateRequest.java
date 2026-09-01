package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 提交订单请求
 */
@Data
public class OrderCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收货地址ID */
    private Long addressId;

    /** 购物车条目ID列表 */
    private List<Long> cartItemIds;

    /** 买家备注 */
    private String remark;
}
