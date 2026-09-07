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

    /** 购物车条目ID列表（购物车结算时使用） */
    private List<Long> cartItemIds;

    /** 订单明细列表（立即购买时使用） */
    private List<OrderItemDTO> orderItems;

    /** 买家备注 */
    private String remark;

    /**
     * 订单明细（立即购买时使用）
     * 注意：价格、小计、商品名称、图片、规格名称等均以服务端数据库为准，
     * 前端仅需传 productId / specId / quantity，其余字段服务端会忽略并重新取值。
     */
    @Data
    public static class OrderItemDTO implements Serializable {
        private Long productId;
        /** 规格ID（有规格商品必传，用于服务端按规格重新计价与后续扣减库存） */
        private Long specId;
        private Integer quantity;
        // 以下字段已废弃（不信任前端），仅保留兼容旧客户端传参，服务端一律忽略
        private String productName;
        private String productImage;
        private String specName;
        private java.math.BigDecimal price;
        private java.math.BigDecimal subtotal;
    }
}
