package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    /** 已分析评价数（口碑证据量，0 表示暂无口碑数据） */
    private Integer reviewCount;

    /** 好评率（百分比 0~100，基于已分析评价；无评价时为 null） */
    private BigDecimal positiveRate;

    /** 好评关键词（买家高频好评标签，最多 5 个） */
    private List<String> topKeywords;
}
