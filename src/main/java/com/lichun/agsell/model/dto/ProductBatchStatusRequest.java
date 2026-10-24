package com.lichun.agsell.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品批量上下架请求（管理端）
 */
@Data
@Schema(description = "商品批量上下架请求")
public class ProductBatchStatusRequest {

    @Schema(description = "商品 ID 列表")
    private List<Long> ids;

    @Schema(description = "目标状态 0=下架 1=上架")
    private Integer status;
}
