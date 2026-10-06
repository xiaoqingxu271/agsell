package com.lichun.agsell.model.vo;

import com.lichun.agsell.model.entity.Review;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品口碑摘要 VO（基于评价情感分析聚合，商品详情页展示）
 */
@Data
public class ReviewSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评价总数（已分析的） */
    private Integer total;

    /** 好评数 */
    private Integer positiveCount;

    /** 中评数 */
    private Integer neutralCount;

    /** 差评数 */
    private Integer negativeCount;

    /** 好评率（百分比，0~100，保留两位） */
    private BigDecimal positiveRate;

    /** 好评关键词标签（出现频次 top5，取自好评评价） */
    private List<String> topKeywords;

    /**
     * 纯聚合逻辑（无 IO），便于单元测试：
     * 情感分布统计 + 好评关键词频次排序
     */
    public static ReviewSummaryVO of(List<Review> reviews) {
        ReviewSummaryVO vo = new ReviewSummaryVO();
        vo.setTotal(0);
        vo.setPositiveCount(0);
        vo.setNeutralCount(0);
        vo.setNegativeCount(0);
        vo.setTopKeywords(List.of());
        if (reviews == null || reviews.isEmpty()) {
            vo.setPositiveRate(BigDecimal.ZERO);
            return vo;
        }

        Map<String, Integer> keywordCount = new HashMap<>();
        for (Review r : reviews) {
            if (r == null) {
                continue;  // select 投影列全 NULL 时 MyBatis 会映射为 null 元素，防御性跳过
            }
            Integer label = r.getSentimentLabel();
            if (label == null) {
                continue;  // 未分析的不计入
            }
            vo.setTotal(vo.getTotal() + 1);
            if (label == 1) {
                vo.setPositiveCount(vo.getPositiveCount() + 1);
                if (r.getSentimentKeywords() != null && !r.getSentimentKeywords().isBlank()) {
                    for (String kw : r.getSentimentKeywords().split(",")) {
                        String k = kw.trim();
                        if (!k.isEmpty()) {
                            keywordCount.merge(k, 1, Integer::sum);
                        }
                    }
                }
            } else if (label == 0) {
                vo.setNeutralCount(vo.getNeutralCount() + 1);
            } else {
                vo.setNegativeCount(vo.getNegativeCount() + 1);
            }
        }

        vo.setPositiveRate(vo.getTotal() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(vo.getPositiveCount() * 100.0 / vo.getTotal())
                        .setScale(2, RoundingMode.HALF_UP));

        vo.setTopKeywords(keywordCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList());
        return vo;
    }
}
