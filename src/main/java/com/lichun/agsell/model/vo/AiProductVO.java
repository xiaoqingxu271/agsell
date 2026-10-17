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

    /**
     * 储存方式 / 保质期：商详页进客服时用户第一问就是"这个能放多久、要不要冷藏"，
     * 这两个字段此前只在 /product/detail 用户接口里有，AI 通道拿不到，
     * 于是知识库有货、导购通道答不出。补齐后 AI 侧的商品事实才完整。
     */
    private String storage;

    private String shelfLife;

    private String mainImage;

    /** 已分析评价数（口碑证据量，0 表示暂无口碑数据） */
    private Integer reviewCount;

    /** 好评率（百分比 0~100，基于已分析评价；无评价时为 null） */
    private BigDecimal positiveRate;

    /** 好评关键词（买家高频好评标签，最多 5 个） */
    private List<String> topKeywords;
}
