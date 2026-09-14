package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
}
