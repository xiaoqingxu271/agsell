package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 订单详情 VO（含商品快照与物流信息，供客服回答"订单/物流到哪了"）
 */
@Data
public class AiOrderDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** 订单状态 0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=售后处理中 6=已退款 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    private BigDecimal totalAmount;

    private BigDecimal freight;

    private BigDecimal discount;

    private BigDecimal payAmount;

    private Integer itemCount;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime receiveTime;

    /** 物流公司 */
    private String logType;

    /** 物流单号 */
    private String logNo;

    private String remark;

    private String cancelReason;

    /** 商品明细快照 */
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String productName;

        private String specName;

        private Integer quantity;

        private BigDecimal price;

        private BigDecimal subtotal;
    }
}
