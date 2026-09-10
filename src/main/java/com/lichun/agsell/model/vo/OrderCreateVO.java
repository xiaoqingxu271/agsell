package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单创建响应
 */
@Data
public class OrderCreateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 订单状态 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 剩余支付秒数（前端待支付页倒计时用；已超时返回 0） */
    private Long expireSeconds;
}
