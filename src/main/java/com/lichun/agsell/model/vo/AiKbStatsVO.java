package com.lichun.agsell.model.vo;

import lombok.Data;

/**
 * AI 知识库与索引状态（管理端「知识库管理」页顶部的状态卡数据源）。
 *
 * 三个数分列是刻意的：faqCount（库里启用的知识条数）、indexedCount（向量库里已条数）、
 * productIndexedCount（商品召回索引条数）。只看第一个会以为"知识都入库了"，
 * 真正决定 AI 能不能答的是后两个——历史上 2.3 那个坑（上新商品召不到）就是这么漏掉的。
 */
@Data
public class AiKbStatsVO {

    /** ai_faq 表中启用的条目数 */
    private long faqCount;

    /** FAQ 向量索引条数 */
    private long indexedCount;

    /** 商品召回索引条数 */
    private long productIndexedCount;

    /** 当前在售商品数（Java 侧统计，用来判断商品索引是否落后） */
    private long onSaleProductCount;

    /** 嵌入模型名 */
    private String embeddingModel;

    /** 知识库检索 top_k */
    private Integer topK;

    /** 商品召回 top_k */
    private Integer productTopK;

    /** 近 7 日被 AI 引用的知识 Top（Python kb_hit_log 聚合；无日志数据为 null） */
    private java.util.List<KbHitTopVO> recentHitTop;

    /**
     * FAQ 是否已同步：启用条数与索引条数不一致就说明索引落后，
     * 管理页据此提示"点全量重建修复"。
     */
    public boolean isFaqSynced() {
        return faqCount == indexedCount;
    }

    /** 商品索引是否已覆盖全部在售商品 */
    public boolean isProductIndexSynced() {
        return productIndexedCount >= onSaleProductCount;
    }
}
