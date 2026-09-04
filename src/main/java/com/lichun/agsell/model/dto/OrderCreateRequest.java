package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
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

    /** 购物车条目ID列表（购物车结算时使用） */
    private List<String> cartItemIds;

    /** 订单明细列表（立即购买时使用） */
    private List<OrderItemDTO> orderItems;

    /** 买家备注 */
    private String remark;

    /** 订单明细（立即购买时使用） */
    @Data
    public static class OrderItemDTO implements Serializable {
        private Long productId;
        private String productName;
        private String productImage;
        private String specName;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
