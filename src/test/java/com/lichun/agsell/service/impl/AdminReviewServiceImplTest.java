package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.vo.AiReviewReplyVO;
import com.lichun.agsell.model.vo.ReviewListItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 管理端评价服务测试：AI 回复草稿 + 列表口碑标签透传（Mockito，不依赖真实 AI 服务）
 */
@ExtendWith(MockitoExtension.class)
class AdminReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ReviewReplyClient reviewReplyClient;

    @InjectMocks
    private AdminReviewServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Review.class);
    }

    @BeforeEach
    void loginAsAdmin() {
        AdminContext.setCurrentAdmin(1L, "ADMIN");
    }

    @AfterEach
    void logout() {
        AdminContext.removeCurrentAdmin();
    }

    private Review review(Long id, Long orderItemId, Long productId, Integer sentimentLabel) {
        Review r = new Review();
        r.setId(id);
        r.setOrderItemId(orderItemId);
        r.setProductId(productId);
        r.setUserId(10L);
        r.setRating(4);
        r.setContent("果子挺新鲜");
        r.setSentimentLabel(sentimentLabel);
        r.setIsAnonymous(0);
        return r;
    }

    @Test
    @DisplayName("AI 回复草稿：查评价 → 取商品名（明细快照优先）→ 调用 AI 客户端 → 返回草稿不落库")
    void generateAiReplyReturnsDraftWithoutPersistence() {
        Review r = review(100L, 55L, 1L, -1);
        when(reviewMapper.selectById(100L)).thenReturn(r);

        OrderItem item = new OrderItem();
        item.setProductName("赣南脐橙 5斤装");
        when(orderItemMapper.selectById(55L)).thenReturn(item);

        ReviewReplyClient.AiReplyDraft draft = new ReviewReplyClient.AiReplyDraft();
        draft.setReply("亲，非常抱歉给您带来不好的体验！坏果包赔，请联系在线客服处理退款～");
        draft.setSource("llm");
        when(reviewReplyClient.generateReply(eq(r), eq("赣南脐橙 5斤装"))).thenReturn(draft);

        AiReviewReplyVO vo = service.generateAiReply(100L);

        assertEquals(100L, vo.getReviewId());
        assertEquals(draft.getReply(), vo.getReply());
        assertEquals("llm", vo.getSource());
        // 草稿不落库：等待管理员编辑后走 replyReview 提交
        verify(reviewMapper, never()).updateById(any(Review.class));
    }

    @Test
    @DisplayName("AI 回复草稿：历史评价无明细快照 → 回退商品表取商品名")
    void generateAiReplyFallsBackToProductTable() {
        Review r = review(101L, null, 2L, 1);
        when(reviewMapper.selectById(101L)).thenReturn(r);

        Product product = new Product();
        product.setId(2L);
        product.setName("五常大米");
        when(productMapper.selectById(2L)).thenReturn(product);

        ReviewReplyClient.AiReplyDraft draft = new ReviewReplyClient.AiReplyDraft();
        draft.setReply("亲，感谢您的认可与支持！");
        draft.setSource("fallback");
        when(reviewReplyClient.generateReply(eq(r), eq("五常大米"))).thenReturn(draft);

        AiReviewReplyVO vo = service.generateAiReply(101L);

        assertEquals("亲，感谢您的认可与支持！", vo.getReply());
        assertEquals("fallback", vo.getSource());
        verify(orderItemMapper, never()).selectById(any(Long.class));
    }

    @Test
    @DisplayName("评价不存在 → 抛业务异常")
    void generateAiReplyRejectsMissingReview() {
        when(reviewMapper.selectById(404L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.generateAiReply(404L));
        verifyNoInteractions(reviewReplyClient);
    }

    @Test
    @DisplayName("未登录管理员 → 拒绝")
    void generateAiReplyRequiresAdminLogin() {
        AdminContext.removeCurrentAdmin();

        assertThrows(BusinessException.class, () -> service.generateAiReply(100L));
        verify(reviewMapper, never()).selectById(any(Long.class));
    }

    @Test
    @DisplayName("评价列表：口碑标签随 VO 透传（此前恒显示「未分析」的 bug 修复）")
    void listReviewsCarriesSentimentLabel() {
        Review analyzed = review(1L, null, 1L, -1);
        Review pending = review(2L, null, 1L, null);
        Page<Review> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(analyzed, pending));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        // orderItemId 均为 null → 明细快照批量查询不会触发；商品表回退查询会触发
        when(productMapper.selectBatchIds(any())).thenReturn(List.of());

        Page<ReviewListItemVO> result = service.listReviews(1, 10, null, null);

        assertEquals(-1, result.getRecords().get(0).getSentimentLabel());
        assertNull(result.getRecords().get(1).getSentimentLabel());
    }
}
