package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 订单列表项 VO（紧凑信息，不返回收货人/电话/地址等隐私）
 */
@Data
public class AiOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** 订单状态 0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=售后处理中 6=已退款 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    private BigDecimal payAmount;

    private Integer itemCount;

    private LocalDateTime createTime;
}
