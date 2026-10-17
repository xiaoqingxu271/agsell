package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表 VO（用户端/管理端共用）
 */
@Data
public class ProductListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String subtitle;

    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private Integer sales;

    private String mainImage;

    /** AI 推荐可解释理由（如"偏爱「乌龙普洱」""日期新鲜"），仅个性化 feed 命中时填充 */
    private String recommendReason;

    private Integer status;

    private LocalDateTime createTime;
}
