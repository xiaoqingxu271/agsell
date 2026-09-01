package com.lichun.agsell.model.vo;

import com.lichun.agsell.model.dto.ProductCreateRequest;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情 VO
 */
@Data
public class ProductDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String subtitle;

    /** 分类名称 */
    private String categoryName;

    /** 父分类名称 */
    private String parentCategoryName;

    private Long categoryId;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private Integer sales;

    private String mainImage;

    private List<String> images;

    private String description;

    private String origin;

    private LocalDate harvestDate;

    private String shelfLife;

    private String storage;

    private Integer status;

    /** 规格列表 */
    private List<ProductSpecVO> specs;

    private LocalDateTime createTime;

    @Data
    public static class ProductSpecVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long id;
        private String specName;
        private BigDecimal price;
        private Integer stock;
        private String image;
    }
}
