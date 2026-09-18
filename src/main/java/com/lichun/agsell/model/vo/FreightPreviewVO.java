package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 运费预估响应（订单确认页调用，前端只展示不计算，保证口径与下单一致）
 */
@Data
public class FreightPreviewVO implements Serializable {

    /** 商品金额 */
    private BigDecimal totalAmount;

    /** 预估运费 */
    private BigDecimal freight;

    /** 应付金额 = 商品金额 + 运费 */
    private BigDecimal payAmount;
}
