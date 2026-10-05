package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 智能导购推荐商品 VO（紧凑卡片信息，供 LLM 生成推荐话术）
 */
@Data
public class AiProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 副标题（卖点） */
    private String subtitle;

    private BigDecimal price;

    /** 原价（划线价） */
    private BigDecimal originalPrice;

    /** 销量 */
    private Integer sales;

    /** 产地（农产品特色字段） */
    private String origin;

    /** 采摘/收获日期（新鲜度参考） */
    private LocalDate harvestDate;

    private String mainImage;
}
