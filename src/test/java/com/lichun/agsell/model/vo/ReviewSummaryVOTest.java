package com.lichun.agsell.model.vo;

import com.lichun.agsell.model.entity.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 口碑摘要聚合纯逻辑测试：情感分布、好评率、关键词频次
 */
class ReviewSummaryVOTest {

    private Review review(Integer label, String keywords) {
        Review r = new Review();
        r.setSentimentLabel(label);
        r.setSentimentKeywords(keywords);
        return r;
    }

    @Test
    @DisplayName("空列表：total=0，好评率=0")
    void empty() {
        ReviewSummaryVO vo = ReviewSummaryVO.of(List.of());
        assertEquals(0, vo.getTotal());
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(vo.getPositiveRate()));
        assertTrue(vo.getTopKeywords().isEmpty());
    }

    @Test
    @DisplayName("未分析的评价不计入统计")
    void skipsUnanalyzed() {
        ReviewSummaryVO vo = ReviewSummaryVO.of(List.of(review(null, null), review(1, "新鲜")));
        assertEquals(1, vo.getTotal());
        assertEquals(1, vo.getPositiveCount());
    }

    @Test
    @DisplayName("情感分布与好评率计算")
    void distributionAndRate() {
        ReviewSummaryVO vo = ReviewSummaryVO.of(List.of(
                review(1, "新鲜"), review(1, "好吃"), review(0, null), review(-1, "坏果")));
        assertEquals(4, vo.getTotal());
        assertEquals(2, vo.getPositiveCount());
        assertEquals(1, vo.getNeutralCount());
        assertEquals(1, vo.getNegativeCount());
        assertEquals(0, java.math.BigDecimal.valueOf(50.00).compareTo(vo.getPositiveRate()));
    }

    @Test
    @DisplayName("好评关键词按频次取 top5，差评关键词不进标签")
    void topKeywords() {
        ReviewSummaryVO vo = ReviewSummaryVO.of(List.of(
                review(1, "新鲜,好吃"), review(1, "新鲜,好吃"), review(1, "新鲜,快"),
                review(-1, "坏果,烂")));
        // 频次：新鲜 3 > 好吃 2 > 快 1（并列频次无序，测试数据避免并列）
        assertEquals(List.of("新鲜", "好吃", "快"), vo.getTopKeywords());
    }
}
