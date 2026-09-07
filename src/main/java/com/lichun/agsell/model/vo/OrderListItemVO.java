package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项 VO
 */
@Data
public class OrderListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNo;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    /** 订单状态 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    /** 商品数量 */
    private Integer itemCount;

    private LocalDateTime createTime;
}
