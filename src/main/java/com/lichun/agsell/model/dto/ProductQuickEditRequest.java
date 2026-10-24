package com.lichun.agsell.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品快速编辑请求（列表内联改价格/库存，不必打开完整编辑弹窗）
 */
@Data
@Schema(description = "商品快速编辑请求")
public class ProductQuickEditRequest {

    @Schema(description = "售价（不传不改）")
    private BigDecimal price;

    @Schema(description = "库存（不传不改；无规格商品改总库存）")
    private Integer stock;
}
