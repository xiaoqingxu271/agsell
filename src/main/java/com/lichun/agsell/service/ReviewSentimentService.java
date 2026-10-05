package com.lichun.agsell.service;

import com.lichun.agsell.model.vo.ReviewSentimentSyncVO;
import com.lichun.agsell.model.vo.ReviewSummaryVO;

/**
 * 评价情感分析服务：管理端触发同步 + 商品口碑摘要聚合
 */
public interface ReviewSentimentService {

    /** 每次触发最多分析的评价数（分批防止超时） */
    int SYNC_BATCH_SIZE = 100;

    /**
     * 同步分析未处理评价：拉取 sentiment_label IS NULL 的评价 →
     * 调 Python AI 服务批量分析 → 情感结果回写 review 表。
     *
     * @param productId 指定商品（null = 全部商品）
     * @return 本次分析数 + 剩余未分析数
     */
    ReviewSentimentSyncVO syncSentiment(Long productId);

    /**
     * 商品口碑摘要聚合（商品详情页展示）
     */
    ReviewSummaryVO getProductReviewSummary(Long productId);
}
