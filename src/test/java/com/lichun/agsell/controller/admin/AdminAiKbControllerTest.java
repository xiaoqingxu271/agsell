package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lichun.agsell.client.AiKbClient;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.dto.AiFaqRequest;
import com.lichun.agsell.model.vo.AiFaqPageVO;
import com.lichun.agsell.model.vo.AiKbStatsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 知识库管理端点：参数校验挡在调用 Python 之前，状态卡合并 Java 侧的在售商品数。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiKbControllerTest {

    @Mock
    private AiKbClient aiKbClient;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private AdminAiKbController controller;

    @Test
    @DisplayName("状态接口把 Python 的索引条数与 Java 的在售商品数合成一张卡")
    void statsMergesOnSaleCountFromLocalDb() {
        AiKbStatsVO stats = new AiKbStatsVO();
        stats.setProductIndexedCount(200);
        stats.setFaqCount(280);
        stats.setIndexedCount(280);
        when(aiKbClient.stats()).thenReturn(stats);
        when(productMapper.selectCount(any(Wrapper.class))).thenReturn(202L);

        BaseResponse<AiKbStatsVO> resp = controller.stats();
        assertEquals(0, resp.getCode());
        assertEquals(202, resp.getData().getOnSaleProductCount());
        // 索引 200 < 在售 202：页面据此提示"索引落后，可点全量重建"
        assertEquals(false, resp.getData().isProductIndexSynced());
    }

    @Test
    @DisplayName("空问法/空答案在 Java 侧就拒绝，不发一次注定失败的跨服务调用")
    void createValidatesBeforeProxying() {
        AiFaqRequest blank = new AiFaqRequest();
        blank.setQuestion("  ");
        blank.setAnswer("有答案");
        BusinessException ex = assertThrows(BusinessException.class, () -> controller.createFaq(blank));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(aiKbClient, never()).createFaq(any());

        AiFaqRequest noAnswer = new AiFaqRequest();
        noAnswer.setQuestion("运费怎么算");
        assertThrows(BusinessException.class, () -> controller.createFaq(noAnswer));
        verify(aiKbClient, never()).createFaq(any());
    }

    @Test
    @DisplayName("超长问法与关键词按 Python 的列宽提前拦下")
    void createRejectsOverlongFields() {
        AiFaqRequest request = new AiFaqRequest();
        request.setQuestion("一".repeat(256));
        request.setAnswer("ok");
        assertThrows(BusinessException.class, () -> controller.createFaq(request));

        AiFaqRequest longKeywords = new AiFaqRequest();
        longKeywords.setQuestion("运费怎么算");
        longKeywords.setAnswer("ok");
        longKeywords.setKeywords("词".repeat(256));
        assertThrows(BusinessException.class, () -> controller.createFaq(longKeywords));
        verify(aiKbClient, never()).createFaq(any());
    }

    @Test
    @DisplayName("编辑与删除都校验 id，避免把 {id=0} 这类请求送到 Python")
    void writePathsValidateId() {
        assertThrows(BusinessException.class, () -> controller.updateFaq(0L, new AiFaqRequest()));
        assertThrows(BusinessException.class, () -> controller.deleteFaq(null));
        verify(aiKbClient, never()).deleteFaq(0L);

        controller.deleteFaq(12L);
        verify(aiKbClient).deleteFaq(12L);
    }

    @Test
    @DisplayName("列表分页参数透传：Python 自己限幅（size 最大 100）")
    void listPassesPagingThrough() {
        AiFaqPageVO page = new AiFaqPageVO();
        page.setTotal(280);
        when(aiKbClient.listFaqs("shipping", 2, 10)).thenReturn(page);
        assertEquals(280, controller.listFaqs("shipping", 2, 10).getData().getTotal());
    }
}
