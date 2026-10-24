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
    @Mock
    private com.lichun.agsell.mapper.UserSearchLogMapper userSearchLogMapper;

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
                    () -> searchService.search(buildRequest(kw), null));
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
                () -> searchService.search(buildRequest(kw), null));
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

        Page<ProductSearchVO> result = searchService.search(buildRequest("橙"), null);

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
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), null);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), eq(List.of()));
        verify(productSearchMapper).countByFulltext("脐橙", null, List.of());
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("分页规整：pageNum<1 归 1，pageSize>50 压到 50，pageSize=0 归默认12")
    void paginationNormalized() {
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setPageNum(0);
        req.setPageSize(999);
        searchService.search(req, null);
        // pageNum=0→1 => offset=0；pageSize=999→50
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(50), eq(List.of()));

        SearchRequest req2 = buildRequest("脐橙");
        req2.setPageSize(0);
        searchService.search(req2, null);
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), eq(List.of()));
    }

    @Test
    @DisplayName("分类过滤透传：categoryId 进入全文检索条件")
    void categoryIdPassed() {
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setCategoryId(209L);
        searchService.search(req, null);

        verify(productSearchMapper).searchByFulltext(eq("脐橙"), eq(209L), eq(""), eq(0L), eq(12), eq(List.of()));
        verify(productSearchMapper).countByFulltext("脐橙", 209L, List.of());
    }

    @Test
    @DisplayName("排序透传：sortBy=hot 进入全文检索")
    void sortByPassed() {
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setSortBy("hot");
        searchService.search(req, null);

        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq("hot"), eq(0L), eq(12), eq(List.of()));
    }

    @Test
    @DisplayName("结果高亮：name/subtitle 命中关键词被 em 包裹")
    void highlightGenerated() {
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(List.of(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), null);
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

        when(aiSearchClient.semanticProductIds(eq("脐橙"), eq(12), isNull())).thenReturn(List.of(1L));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(semHit));
        // 语义仅 1 条 < pageSize，用全文补足（id=9 不在语义集）
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), isNull()))
                .thenReturn(List.of(ftHit));
        when(productSearchMapper.countByFulltext(eq("脐橙"), isNull(), isNull())).thenReturn(5L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), null);

        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());  // 语义命中排在前
        assertEquals(9L, result.getRecords().get(1).getId());  // 全文补足在后
        assertEquals(5, result.getTotal());
    }

    @Test
    @DisplayName("AI 语义全空：完全退回全文检索路径")
    void semanticEmptyFallsBackToFulltext() {
        when(aiSearchClient.semanticProductIds(eq("脐橙"), eq(12), isNull())).thenReturn(Collections.emptyList());
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(List.of(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), null);

        assertEquals(1, result.getRecords().size());
        verify(productMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("翻页：语义不再置顶，但要拿语义首页那几条去重并把偏移回退")
    void nextPageDeduplicatesSemanticHead() {
        Product semHit = mockProduct();
        semHit.setId(1L);
        when(aiSearchClient.semanticProductIds(eq("脐橙"), eq(12), isNull())).thenReturn(List.of(1L));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(semHit));
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(23L);

        SearchRequest req = buildRequest("脐橙");
        req.setPageNum(2);
        req.setPageSize(12);
        Page<ProductSearchVO> result = searchService.search(req, null);

        // 虚拟列表 = 语义 k=1 条 + 全文去掉这 1 条；第 2 页偏移 = 12-1 = 11，并排除 id=1
        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(11L), eq(12), eq(List.of(1L)));
        verify(productSearchMapper).countByFulltext("脐橙", null, List.of(1L));
        // total 口径与虚拟列表一致：1 + 23
        assertEquals(24, result.getTotal());
    }

    @Test
    @DisplayName("翻页时 AI 服务不可用：退回纯全文口径，不去重也不报错")
    void nextPageWithoutAiKeepsPlainFulltext() {
        when(aiSearchClient.semanticProductIds(eq("脐橙"), eq(12), isNull())).thenReturn(Collections.emptyList());
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(24L);

        SearchRequest req = buildRequest("脐橙");
        req.setPageNum(2);
        req.setPageSize(12);
        Page<ProductSearchVO> result = searchService.search(req, null);

        verify(productSearchMapper).searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(12L), eq(12), eq(List.of()));
        assertEquals(24, result.getTotal());
    }

    @Test
    @DisplayName("第 2 页起带分类过滤/排序：判据与首页一致，不触发语义去重")
    void nonSemanticLanePagesDoNotCallAi() {
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);

        SearchRequest byCategory = buildRequest("脐橙");
        byCategory.setPageNum(2);
        byCategory.setCategoryId(209L);
        searchService.search(byCategory, null);

        SearchRequest bySort = buildRequest("脐橙");
        bySort.setPageNum(3);
        bySort.setSortBy("hot");
        searchService.search(bySort, null);

        verifyNoInteractions(aiSearchClient);
    }

    @Test
    @DisplayName("带分类过滤时不走语义叠加，保持全文+分类口径")
    void categoryFilterSkipsSemantic() {
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);

        SearchRequest req = buildRequest("脐橙");
        req.setCategoryId(209L);
        searchService.search(req, null);

        verifyNoInteractions(aiSearchClient);
    }

    @Test
    @DisplayName("热词记录异常不影响搜索主流程（容错）")
    void hotWordFailureIsolated() {
        doThrow(new RuntimeException("db down")).when(hotWordService).record(anyString());
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), null);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("无结果：返回空列表且 total=0，不抛异常")
    void emptyResult() {
        when(productSearchMapper.countByFulltext(anyString(), any(), anyList())).thenReturn(0L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = searchService.search(buildRequest("不存在的商品"), null);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("全文检索异常（如 FULLTEXT 索引缺失）：降级 LIKE，绝不向上抛给接口层")
    void fulltextFailureFallsBackToLike() {
        // 模拟真实故障：MATCH AGAINST 报 "Can't find FULLTEXT index matching the column list"
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), anyList()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        Product p = mockProduct();
        Page<Product> likePage = new Page<>(1, 12);
        likePage.setRecords(Collections.singletonList(p));
        likePage.setTotal(1);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(likePage);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        SearchRequest req = buildRequest("脐橙");
        req.setSortBy("hot");
        Page<ProductSearchVO> result = assertDoesNotThrow(() -> searchService.search(req, null));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(productMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("语义首页补足失败：保住语义命中结果，不影响当页返回")
    void semanticBackfillFailureKeepsSemanticHits() {
        Product semHit = mockProduct();
        when(aiSearchClient.semanticProductIds(eq("送礼"), eq(12), isNull())).thenReturn(Collections.singletonList(1L));
        when(productMapper.selectBatchIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(semHit));
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), any()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        when(productSearchMapper.countByFulltext(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Can't find FULLTEXT index matching the column list"));
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSearchVO> result = assertDoesNotThrow(() -> searchService.search(buildRequest("送礼"), null));

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("登录用户搜索：userId 透传给语义召回（否则 Python 侧偏好分支恒空，个性化形同虚设）")
    void userIdPassedIntoSemanticRecallRequest() {
        Product semHit = mockProduct();
        when(aiSearchClient.semanticProductIds(eq("脐橙"), eq(12), eq(7L))).thenReturn(List.of(1L));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(semHit));
        when(productSearchMapper.countByFulltext(eq("脐橙"), isNull(), isNull())).thenReturn(1L);
        when(productSearchMapper.searchByFulltext(anyString(), any(), anyString(), anyLong(), anyInt(), isNull()))
                .thenReturn(Collections.emptyList());
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        searchService.search(buildRequest("脐橙"), 7L);

        verify(aiSearchClient).semanticProductIds("脐橙", 12, 7L);
    }

    @Test
    @DisplayName("登录用户搜索：关键词落 user_search_log（AI 偏好信号）")
    void loggedInSearchWritesSearchLog() {
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(userSearchLogMapper.insert(any(com.lichun.agsell.model.entity.UserSearchLog.class))).thenReturn(1);

        searchService.search(buildRequest("脐橙"), 9L);

        org.mockito.ArgumentCaptor<com.lichun.agsell.model.entity.UserSearchLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.lichun.agsell.model.entity.UserSearchLog.class);
        verify(userSearchLogMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals("脐橙", captor.getValue().getKeyword());
    }

    @Test
    @DisplayName("搜索词记录失败只降级为丢一条信号，绝不让搜索接口报错")
    void searchLogFailureDoesNotBreakSearch() {
        Product p = mockProduct();
        when(productSearchMapper.searchByFulltext(eq("脐橙"), isNull(), eq(""), eq(0L), eq(12), anyList()))
                .thenReturn(Collections.singletonList(p));
        when(productSearchMapper.countByFulltext("脐橙", null, List.of())).thenReturn(1L);
        when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(userSearchLogMapper.insert(any(com.lichun.agsell.model.entity.UserSearchLog.class)))
                .thenThrow(new RuntimeException("db down"));

        Page<ProductSearchVO> result = searchService.search(buildRequest("脐橙"), 9L);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }
}
