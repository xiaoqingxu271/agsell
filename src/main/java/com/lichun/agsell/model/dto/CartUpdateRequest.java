package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改购物车数量请求
 */
@Data
public class CartUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数量 */
    private Integer quantity;
}
