package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.AiTicketMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.model.dto.AiProductSearchRequest;
import com.lichun.agsell.model.dto.AiTicketCreateRequest;
import com.lichun.agsell.model.entity.AiTicket;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.vo.AiProductVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 导购商品搜索 + 口碑聚合测试（Mockito，不依赖真实数据库/Python 服务）
 */
@ExtendWith(MockitoExtension.class)
class AiInternalControllerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private AfterSalesMapper afterSalesMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private AiTicketMapper aiTicketMapper;

    @InjectMocks
    private AiInternalController controller;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, Review.class);
    }

    private Product product(long id, String name, int sales) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setStatus(1);
        p.setPrice(BigDecimal.valueOf(19.9));
        p.setSales(sales);
        p.setOrigin("江西赣州");
        return p;
    }

    private Review analyzedReview(long productId, Integer label, String keywords) {
        Review r = new Review();
        r.setProductId(productId);
        r.setSentimentLabel(label);
        r.setSentimentKeywords(keywords);
        return r;
    }

    @Test
    @DisplayName("导购搜索：候选商品附带口碑摘要（评价数/好评率/好评关键词）")
    void productSearchAttachesReputation() {
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(1L, "赣南脐橙", 500), product(2L, "五常大米", 300)));
        // 商品1：3 条已分析评价（2 好评 1 差评，好评关键词"新鲜"出现两次）；商品2：暂无评价
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                analyzedReview(1L, 1, "新鲜,甜"),
                analyzedReview(1L, 1, "新鲜,好吃"),
                analyzedReview(1L, -1, "坏果")));

        BaseResponse<List<AiProductVO>> resp = controller.productSearch(new AiProductSearchRequest());
        List<AiProductVO> data = resp.getData();

        assertEquals(2, data.size());

        AiProductVO vo1 = data.get(0);
        assertEquals(3, vo1.getReviewCount());
        assertEquals(0, BigDecimal.valueOf(66.67).compareTo(vo1.getPositiveRate()));
        assertEquals(List.of("新鲜", "甜", "好吃"), vo1.getTopKeywords());

        // 无评价商品：reviewCount=0 且好评率为 null（明确告知 LLM"暂无口碑数据"，避免编造）
        AiProductVO vo2 = data.get(1);
        assertEquals(0, vo2.getReviewCount());
        assertNull(vo2.getPositiveRate());
        assertNull(vo2.getTopKeywords());
    }

    @Test
    @DisplayName("未分析评价不计入口碑：全部 NULL 时等同无口碑数据")
    void productSearchSkipsUnanalyzedReviews() {
        when(productMapper.selectList(any())).thenReturn(List.of(product(1L, "赣南脐橙", 500)));
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                analyzedReview(1L, null, null), analyzedReview(1L, null, null)));

        List<AiProductVO> data = controller.productSearch(new AiProductSearchRequest()).getData();

        assertEquals(1, data.size());
        assertEquals(0, data.get(0).getReviewCount());
        assertNull(data.get(0).getPositiveRate());
        assertNull(data.get(0).getTopKeywords());
    }

    @Test
    @DisplayName("转人工工单落库：游客（userId 为空）同样接受，入参 trim 后入库")
    void createTicketAcceptsGuestAndTrims() {
        when(aiTicketMapper.insert(any(AiTicket.class))).thenReturn(1);

        AiTicketCreateRequest req = new AiTicketCreateRequest();
        req.setSessionId("  wx_abc  ");
        req.setQuestion("  我要投诉配送  ");
        req.setReason("用户明确要求人工跟进");

        BaseResponse<Long> resp = controller.createTicket(req);

        assertEquals(0, resp.getCode());
        ArgumentCaptor<AiTicket> captor = ArgumentCaptor.forClass(AiTicket.class);
        verify(aiTicketMapper).insert(captor.capture());
        AiTicket saved = captor.getValue();
        assertEquals("wx_abc", saved.getSessionId());
        assertEquals("我要投诉配送", saved.getQuestion());
        assertEquals(0, saved.getStatus());
        assertEquals(null, saved.getUserId());
    }

    @Test
    @DisplayName("空 sessionId / 空 question 在 Java 侧拒绝，不产生空壳工单")
    void createTicketValidatesRequiredFields() {
        AiTicketCreateRequest noSession = new AiTicketCreateRequest();
        noSession.setSessionId(" ");
        noSession.setQuestion("q");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.createTicket(noSession)).getCode());

        AiTicketCreateRequest noQuestion = new AiTicketCreateRequest();
        noQuestion.setSessionId("s");
        noQuestion.setQuestion("   ");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.createTicket(noQuestion)).getCode());

        verify(aiTicketMapper, never()).insert(any(AiTicket.class));
    }
}
