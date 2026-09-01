package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 加入购物车请求
 */
@Data
public class CartAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long productId;

    /** 规格ID */
    private Long specId;

    /** 购买数量 */
    private Integer quantity = 1;
}
