package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lichun.agsell.client.AiKbClient;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductCategory;
import com.lichun.agsell.support.MpLambdaCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 链路 B 修复验证：商品/规格数据校验、删除订单关联商品保护
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    /**
     * 商品写路径现在会通知 ai-service 更新召回索引。这个 mock 同时承担两件事：
     * 让构造注入拿到非 null 依赖（否则删除/上下架会 NPE），并让下面的测试能验证"有没有通知"。
     */
    @Mock
    private AiKbClient aiKbClient;

    @InjectMocks
    private ProductServiceImpl productService;

    @org.junit.jupiter.api.BeforeAll
    static void initLambdaCache() {
        // 批量上下架用 LambdaUpdateWrapper.in(Product::getId, ...)，链式调用时即解析列名
        MpLambdaCache.init(Product.class);
    }

    private ProductCreateRequest buildValidRequest() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("测试商品");
        request.setCategoryId(1L);
        request.setPrice(new BigDecimal("19.90"));
        request.setStock(100);
        return request;
    }

    @Test
    @DisplayName("分类不存在时新增商品报参数错误")
    void createProductWithMissingCategoryFails() {
        ProductCreateRequest request = buildValidRequest();
        when(categoryMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.saveOrUpdateProduct(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("规格名称为空时报参数错误")
    void createProductWithBlankSpecFails() {
        ProductCreateRequest request = buildValidRequest();
        ProductCreateRequest.ProductSpecDTO spec = new ProductCreateRequest.ProductSpecDTO();
        spec.setSpecName(" ");
        spec.setPrice(new BigDecimal("19.90"));
        spec.setStock(10);
        request.setSpecs(List.of(spec));

        when(categoryMapper.selectById(1L)).thenReturn(new ProductCategory());
        when(productMapper.insert(any(Product.class))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.saveOrUpdateProduct(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(productSpecMapper, never()).insert(anyList());
    }

    @Test
    @DisplayName("规格价格为负数时报参数错误")
    void createProductWithNegativeSpecPriceFails() {
        ProductCreateRequest request = buildValidRequest();
        ProductCreateRequest.ProductSpecDTO spec = new ProductCreateRequest.ProductSpecDTO();
        spec.setSpecName("标准装");
        spec.setPrice(new BigDecimal("-1"));
        spec.setStock(10);
        request.setSpecs(List.of(spec));

        when(categoryMapper.selectById(1L)).thenReturn(new ProductCategory());
        when(productMapper.insert(any(Product.class))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.saveOrUpdateProduct(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("删除被订单引用的商品应被拒绝")
    void deleteProductWithOrderReferenceFails() {
        Product exist = new Product();
        exist.setId(1L);
        when(productMapper.selectById(1L)).thenReturn(exist);
        when(orderItemMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.deleteProduct(1L));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        verify(productMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("无订单引用的商品可正常删除")
    void deleteProductWithoutReferenceSucceeds() {
        Product exist = new Product();
        exist.setId(2L);
        when(productMapper.selectById(2L)).thenReturn(exist);
        when(orderItemMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(productSpecMapper.delete(any(Wrapper.class))).thenReturn(0);
        when(productMapper.deleteById(2L)).thenReturn(1);

        productService.deleteProduct(2L);
        verify(productMapper).deleteById(2L);
        // 删除必须同步通知索引除名：留在向量库里，导购就会推出一件已经买不到的商品
        verify(aiKbClient).notifyProductIndexAsync(2L);
    }

    @Test
    @DisplayName("上下架后通知召回索引（上架进索引、下架除名由 Python 按在售状态判断）")
    void statusChangeNotifiesProductIndex() {
        Product exist = new Product();
        exist.setId(5L);
        when(productMapper.selectById(5L)).thenReturn(exist);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateProductStatus(5L, 1);
        verify(aiKbClient).notifyProductIndexAsync(5L);
    }

    @Test
    @DisplayName("新增商品成功后用新商品 id 通知索引（上新立刻可被 AI 召回）")
    void createProductNotifiesIndexWithNewId() {
        ProductCreateRequest request = buildValidRequest();
        when(categoryMapper.selectById(1L)).thenReturn(new ProductCategory());
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Product.class).setId(88L);
            return 1;
        });

        productService.saveOrUpdateProduct(request);
        verify(aiKbClient).notifyProductIndexAsync(88L);
    }

    @Test
    @DisplayName("编辑商品也要通知（名称/卖点/产地变化会改变语义召回结果）")
    void updateProductNotifiesIndex() {
        ProductCreateRequest request = buildValidRequest();
        request.setId(66L);
        when(categoryMapper.selectById(1L)).thenReturn(new ProductCategory());
        when(productMapper.selectById(66L)).thenReturn(new Product());
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.saveOrUpdateProduct(request);
        verify(aiKbClient).notifyProductIndexAsync(66L);
    }

    // ---------- 批量上下架（P1 §5.3.1） ----------

    @Test
    @DisplayName("批量上下架：整批一条 UPDATE，索引通知整批一次（去重去 null）")
    void batchUpdateStatusUpdatesAndNotifiesOnce() {
        when(productMapper.update(any(), any(Wrapper.class))).thenReturn(3);

        int updated = productService.batchUpdateStatus(Arrays.asList(1L, 2L, 2L, null, 3L), 1);

        assertEquals(3, updated);
        verify(aiKbClient).notifyProductIndexAsync(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("批量上下架：空选择/空入参/非法状态在服务层拒绝")
    void batchUpdateStatusRejectsBadInput() {
        assertThrows(BusinessException.class, () -> productService.batchUpdateStatus(List.of(), 1));
        assertThrows(BusinessException.class, () -> productService.batchUpdateStatus(null, 1));
        assertThrows(BusinessException.class, () -> productService.batchUpdateStatus(List.of(1L), 2));
        verify(productMapper, never()).update(any(), any(Wrapper.class));
        verify(aiKbClient, never()).notifyProductIndexAsync(anyList());
    }

    // ---------- 内联快速编辑价格/库存（P1 §5.3.1） ----------

    @Test
    @DisplayName("快速编辑：只改传了的字段；价格/库存不在索引文本里、不通知 AI 侧")
    void quickEditUpdatesProvidedFieldsOnly() {
        when(productMapper.selectById(1L)).thenReturn(new Product());
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.quickEditPriceStock(1L, new BigDecimal("29.90"), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("29.90"), captor.getValue().getPrice());
        assertNull(captor.getValue().getStock());
        verify(aiKbClient, never()).notifyProductIndexAsync(anyList());
    }

    @Test
    @DisplayName("快速编辑：负价格/超两位小数/负库存/两项全空/商品不存在 → 拒绝")
    void quickEditRejectsBadInput() {
        assertThrows(BusinessException.class,
                () -> productService.quickEditPriceStock(1L, null, null));
        assertThrows(BusinessException.class,
                () -> productService.quickEditPriceStock(1L, new BigDecimal("-1"), null));
        assertThrows(BusinessException.class,
                () -> productService.quickEditPriceStock(1L, new BigDecimal("9.999"), null));
        assertThrows(BusinessException.class,
                () -> productService.quickEditPriceStock(1L, null, -5));
        when(productMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> productService.quickEditPriceStock(404L, new BigDecimal("9.90"), 10));
        verify(productMapper, never()).updateById(any(Product.class));
    }
}
