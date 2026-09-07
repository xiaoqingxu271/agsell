package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车条目 VO
 */
@Data
public class CartItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    private String productName;

    private String productImage;

    private Long specId;

    private String specName;

    private BigDecimal price;

    private Integer quantity;

    private Integer selected;

    /** 小计 */
    private BigDecimal subtotal;

    private Integer stock;

    /** 是否有效 1=有效 0=失效（商品已下架/已删除） */
    private Integer valid;

    /** 失效原因 */
    private String invalidReason;

    private java.time.LocalDateTime createTime;
}
