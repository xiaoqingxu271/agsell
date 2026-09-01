package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情 VO
 */
@Data
public class OrderDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private BigDecimal freight;

    private BigDecimal discount;

    /** 订单状态 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    private String receiver;

    private String phone;

    private String address;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime receiveTime;

    private String logType;

    private String logNo;

    private String cancelReason;

    /** 订单明细 */
    private List<OrderItemVO> items;

    @Data
    public static class OrderItemVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long productId;

        private String productName;

        private String productImage;

        private String specName;

        private BigDecimal price;

        private Integer quantity;

        private BigDecimal subtotal;
    }
}
