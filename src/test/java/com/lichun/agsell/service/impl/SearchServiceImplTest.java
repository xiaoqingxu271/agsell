package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.client.AiSearchClient;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSearchMapper;
import com.lichun.agsell.model.dto.SearchRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.vo.ProductSearchVO;
import com.lichun.agsell.service.HotWordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 搜索服务边界测试：参数校验、单字降级路由、分页规整、高亮、热词容错
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ProductSearchMapper productSearchMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private HotWordService hotWordService;
    @Mock
    private AiSearchClient aiSearchClient;

    @InjectMocks
    private SearchServiceImpl searchService;

    private Product mockProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setName("赣南脐橙 新鲜当季");
        p.setSubtitle("产地直发 坏果包赔");
        p.setCategoryId(10L);
        p.setPrice(new BigDecimal("39.90"));
        p.setStatus(1);
        return p;
    }

    private SearchRequest buildRequest(String keyword) {
        SearchRequest req = new SearchRequest();
        req.setKeyword(keyword);
        return req;
    }

    @Test
    @DisplayName("空关键词：拒绝并返回参数错误")
    void blankKeywordRejected() {
        for (String kw : new String[]{null, "", "   "}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> searchService.search(buildRequest(kw)));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        }
        verifyNoInteractions(hotWordService);
        verifyNoInteractions(productSearchMapper);
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("超长关键词（51字符）：拒绝并返回参数错误")
    void tooLongKeywordRejected() {
        String kw = "橙".repeat(51);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> searchService.search(buildRequest(kw)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("单字符关键词：降级走 LIKE（productMapper.selectPage），不触达全文索引")
    void singleCharFallsBackToLike() {
        Product p = mockProduct();
        Page<Product> page = new Page<>(1, 12);
        page.setRecords(Collections.singletonList(p));
        page.setTotal(1);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("橙"));

        assertEquals(1, result.getTotal());
        verify(productMapper).selectPage(any(Page.class), any());
        verifyNoInteractions(productSearchMapper);
        // 热词仍记录
        verify(hotWordService).record("橙");
    }

    @Test
    @DisplayName("双字符关键词：走 FULLTEXT（searchByFulltext + countByFulltext）")
    void multiCharUsesFulltext() {
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12)))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null)).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12));
        verify(productSearchMapper).countByFulltext("脐橙", null);
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("分页规整：pageNum<1 归 1，pageSize>50 压到 50，pageSize=0 归默认12")
    void paginationNormalized() {
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setPageNum(0);
        req.setPageSize(999);
        searchService.search(req);
        // pageNum=0→1 => offset=0；pageSize=999→50
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(50));

        SearchRequest req2 = buildRequest("脐橙");
        req2.setPageSize(0);
        searchService.search(req2);
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12));
    }

    @Test
    @DisplayName("分类过滤透传：categoryId 进入全文检索条件")
    void categoryIdPassed() {
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setCategoryId(209L);
        searchService.search(req);

        verify(productSearchMapper).searchByFulltext(eq("脐橙"), eq(209L), eq(""), eq(0L), eq(12));
        verify(productSearchMapper).countByFulltext("脐橙", 209L);
    }

    @Test
    @DisplayName("排序透传：sortBy=hot 进入全文检索")
    void sortByPassed() {
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setSortBy("hot");
        searchService.search(req);

        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq("hot"), eq(0L), eq(12));
    }

    @Test
    @DisplayName("结果高亮：name/subtitle 命中关键词被 em 包裹")
    void highlightGenerated() {
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12)))
                .thenReturn(List.of(p));
        when(productSearchMapper.countByFulltext("脐橙", null)).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"));
        ProductSearchVO vo = result.getRecords().get(0);

        assertEquals("赣南<em>脐橙</em> 新鲜当季", vo.getNameHighlight());
        assertEquals("产地直发 坏果包赔", vo.getSubtitleHighlight());
        assertEquals("赣南脐橙 新鲜当季", vo.getName()); // 原字段不变
    }

    @Test
    @DisplayName("AI 语义命中：语义商品置顶，全文结果去重补足当页")
    void semanticHitsLeadThenFulltextBackfills() {
        Product semHit = mockProduct();      // id=1
        semHit.setId(1L);
        Product ftHit = mockProduct();        // id=9，全文兜底项
        ftHit.setId(9L);
        ftHit.setName("普通橙子");

        when(aiSearchClient.semanticProductIds("脐橙", 12)).thenReturn(List.of(1L));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(semHit));
        // 语义仅 1 条 < pageSize，用全文补足（id=9 不在语义集）
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12)))
                .thenReturn(List.of(ftHit));
        when(productSearchMapper.countByFulltext("脐橙", null)).thenReturn(5L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"));

        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());  // 语义命中排在前
        assertEquals(9L, result.getRecords().get(1).getId());  // 全文补足在后
        assertEquals(5, result.getTotal());
    }

    @Test
    @DisplayName("AI 语义全空：完全退回全文检索路径")
    void semanticEmptyFallsBackToFulltext() {
        when(aiSearchClient.semanticProductIds("脐橙", 12)).thenReturn(Collections.emptyList());
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12)))
                .thenReturn(List.of(p));
        when(productSearchMapper.countByFulltext("脐橙", null)).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"));

        assertEquals(1, result.getRecords().size());
        verify(productMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("翻页（pageNum>1）不走语义，直接全文检索")
    void nextPageSkipsSemantic() {
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);

        SearchRequest req = buildRequest("脐橙");
        req.setPageNum(2);
        searchService.search(req);

        verifyNoInteractions(aiSearchClient);
    }

    @Test
    @DisplayName("带分类过滤时不走语义叠加，保持全文+分类口径")
    void categoryFilterSkipsSemantic() {
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);

        SearchRequest req = buildRequest("脐橙");
        req.setCategoryId(209L);
        searchService.search(req);

        verifyNoInteractions(aiSearchClient);
    }

    @Test
    @DisplayName("热词记录异常不影响搜索主流程（容错）")
    void hotWordFailureIsolated() {
        doThrow(new RuntimeException("db down")).when(hotWordService).record(anyString());
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12)))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null)).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"));
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("无结果：返回空列表且 total=0，不抛异常")
    void emptyResult() {
        when(productSearchMapper.countByFulltext(anyString(), any())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("不存在的商品"));
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("全文检索异常（如 FULLTEXT 索引缺失）：降级 LIKE，绝不向上抛给接口层")
    void fulltextFailureFallsBackToLike() {
        // 模拟真实故障：MATCH AGAINST 报 "Can't find FULLTEXT index matching the column list"
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        Product p = mockProduct();
        Page<Product> likePage = new Page<>(1, 12);
        likePage.setRecords(Collections.singletonList(p));
        likePage.setTotal(1);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(likePage);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setSortBy("hot");
        Page<ProductSearchVO> result = assertDoesNotThrow(() -> searchService.search(req));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(productMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("语义首页补足失败：保住语义命中结果，不影响当页返回")
    void semanticBackfillFailureKeepsSemanticHits() {
        Product semHit = mockProduct();
        when(aiSearchClient.semanticProductIds("送礼", 12)).thenReturn(Collections.singletonList(1L));
        when(productMapper.selectBatchIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(semHit));
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        when(productSearchMapper.countByFulltext(anyString(), any()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = assertDoesNotThrow(() -> searchService.search(buildRequest("送礼")));

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
    }
}
