package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商品新增/编辑请求
 */
@Data
public class ProductCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID（编辑时必填） */
    private Long id;

    /** 商品名称 */
    private String name;

    /** 副标题 */
    private String subtitle;

    /** 分类ID */
    private Long categoryId;

    /** 售价 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 库存 */
    private Integer stock;

    /** 主图URL */
    private String mainImage;

    /** 图片数组JSON */
    private String images;

    /** 商品详情HTML */
    private String description;

    /** 产地 */
    private String origin;

    /** 采摘/上市日期 */
    private LocalDate harvestDate;

    /** 保质期 */
    private String shelfLife;

    /** 储存方式 */
    private String storage;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 排序值 */
    private Integer sort;

    /** 规格列表 */
    private java.util.List<ProductSpecDTO> specs;

    @Data
    public static class ProductSpecDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

    private Long id;
        private String specName;
        private BigDecimal price;
        private Integer stock;
        private String image;
    }
}
