package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.vo.ReviewSentimentSyncVO;
import com.lichun.agsell.model.vo.ReviewSummaryVO;
import com.lichun.agsell.service.ReviewSentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价情感分析服务实现
 * <p>
 * 同步链路：管理端触发 → 拉取未分析评价 → Python /v1/sentiment/batch 批量分析 →
 * 情感结果回写 review 表（label/score/keywords）。
 * 分析器为词库+规则实现（确定性输出），AI 服务离线时同步失败但不影响已有口碑数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSentimentServiceImpl implements ReviewSentimentService {

    private final ReviewMapper reviewMapper;
    private final SentimentClient sentimentClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewSentimentSyncVO syncSentiment(Long productId) {
        List<Review> pending = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(productId != null, Review::getProductId, productId)
                .isNull(Review::getSentimentLabel)
                .orderByAsc(Review::getCreateTime)
                .last("LIMIT " + SYNC_BATCH_SIZE));

        int analyzed = 0;
        if (!pending.isEmpty()) {
            List<SentimentClient.AnalyzedReview> results = sentimentClient.analyzeBatch(pending);
            Map<Long, SentimentClient.AnalyzedReview> byId = results.stream()
                    .collect(Collectors.toMap(SentimentClient.AnalyzedReview::getReviewId, r -> r, (a, b) -> a));

            for (Review review : pending) {
                SentimentClient.AnalyzedReview ar = byId.get(review.getId());
                if (ar == null) {
                    continue;
                }
                Review update = new Review();
                update.setId(review.getId());
                update.setSentimentLabel(ar.getLabel());
                update.setSentimentScore(ar.getScore());
                update.setSentimentKeywords(String.join(",", ar.getKeywords()));
                reviewMapper.updateById(update);
                analyzed++;
            }
            log.info("[口碑分析] 本次分析 {} 条评价（productId={}）", analyzed, productId);
        }
        return new ReviewSentimentSyncVO(analyzed, countRemaining(productId));
    }

    @Override
    public ReviewSummaryVO getProductReviewSummary(Long productId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .select(Review::getSentimentLabel, Review::getSentimentKeywords)
                .eq(Review::getProductId, productId));
        return ReviewSummaryVO.of(reviews);
    }

    private int countRemaining(Long productId) {
        Long count = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(productId != null, Review::getProductId, productId)
                .isNull(Review::getSentimentLabel));
        return count == null ? 0 : count.intValue();
    }
}
