package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的收藏条目 VO（带商品快照，便于列表直出）
 */
@Data
@Schema(description = "我的收藏条目")
public class FavoriteItemVO {

    @Schema(description = "收藏记录ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "主图")
    private String mainImage;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "销量")
    private Integer sales;

    @Schema(description = "商品状态 0=下架 1=上架（下架商品置灰展示）")
    private Integer productStatus;

    @Schema(description = "收藏时间")
    private LocalDateTime favoriteTime;
}
