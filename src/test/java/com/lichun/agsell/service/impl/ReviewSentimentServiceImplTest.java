package com.lichun.agsell.service.impl;

import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.vo.ReviewSentimentSyncVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 评价情感同步链路测试（Mockito，不依赖真实 AI 服务）
 */
@ExtendWith(MockitoExtension.class)
class ReviewSentimentServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private SentimentClient sentimentClient;

    @InjectMocks
    private ReviewSentimentServiceImpl service;

    private Review pendingReview(Long id, String content) {
        Review r = new Review();
        r.setId(id);
        r.setProductId(100L);
        r.setRating(5);
        r.setContent(content);
        return r;
    }

    @Test
    @DisplayName("同步：拉取未分析评价 → 批量分析 → 情感结果回写")
    void syncWritesSentimentBack() {
        Review r1 = pendingReview(1L, "新鲜好吃");
        Review r2 = pendingReview(2L, "坏果，失望");
        when(reviewMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(reviewMapper.selectCount(any())).thenReturn(0L);

        SentimentClient.AnalyzedReview a1 = new SentimentClient.AnalyzedReview();
        a1.setReviewId(1L);
        a1.setLabel(1);
        a1.setScore(new BigDecimal("0.95"));
        a1.setKeywords(List.of("新鲜", "好吃"));
        SentimentClient.AnalyzedReview a2 = new SentimentClient.AnalyzedReview();
        a2.setReviewId(2L);
        a2.setLabel(-1);
        a2.setScore(new BigDecimal("0.10"));
        a2.setKeywords(List.of("坏果"));
        when(sentimentClient.analyzeBatch(any())).thenReturn(List.of(a1, a2));

        ReviewSentimentSyncVO vo = service.syncSentiment(null);

        assertEquals(2, vo.getAnalyzedCount());
        assertEquals(0, vo.getRemainingCount());

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper, times(2)).updateById(captor.capture());
        List<Review> updates = captor.getAllValues();
        assertEquals(1, updates.get(0).getSentimentLabel());
        assertEquals("新鲜,好吃", updates.get(0).getSentimentKeywords());
        assertEquals(-1, updates.get(1).getSentimentLabel());
        assertEquals("坏果", updates.get(1).getSentimentKeywords());
    }

    @Test
    @DisplayName("无未分析评价：不调用 AI 服务，直接返回 0")
    void syncSkipsWhenNothingPending() {
        when(reviewMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectCount(any())).thenReturn(3L);

        ReviewSentimentSyncVO vo = service.syncSentiment(100L);

        assertEquals(0, vo.getAnalyzedCount());
        assertEquals(3, vo.getRemainingCount());
        verifyNoInteractions(sentimentClient);
        verify(reviewMapper, never()).updateById(any(Review.class));
    }
}
