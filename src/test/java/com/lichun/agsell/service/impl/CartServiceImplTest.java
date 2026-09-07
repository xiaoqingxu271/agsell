package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CartMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.dto.CartAddRequest;
import com.lichun.agsell.model.dto.CartUpdateRequest;
import com.lichun.agsell.model.entity.Cart;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.model.vo.CartItemVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 链路 C 修复验证：无规格商品重复入车、下架商品校验、失效商品标记与结算过滤
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private Product buildProduct(Long id, int status, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("赣南脐橙");
        product.setMainImage("https://img.example.com/orange.jpg");
        product.setPrice(new BigDecimal("29.90"));
        product.setStock(stock);
        product.setStatus(status);
        return product;
    }

    private Cart buildCart(Long id, Long productId, Long specId, int quantity, int selected) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(1L);
        cart.setProductId(productId);
        cart.setSpecId(specId);
        cart.setQuantity(quantity);
        cart.setSelected(selected);
        return cart;
    }

    // ==================== C4：无规格商品重复入车 ====================

    @Test
    @DisplayName("无规格商品已存在时累加数量而非重复新增")
    void addToCartNoSpecMergesQuantity() {
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 100));
        Cart exist = buildCart(1L, 10L, null, 2, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        CartAddRequest request = new CartAddRequest();
        request.setProductId(10L);
        request.setQuantity(3);
        cartService.addToCart(request);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(5, captor.getValue().getQuantity());
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    @DisplayName("无规格商品首次加入时走新增")
    void addToCartNoSpecInsertsWhenAbsent() {
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 100));
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        CartAddRequest request = new CartAddRequest();
        request.setProductId(10L);
        request.setQuantity(2);
        cartService.addToCart(request);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).insert(captor.capture());
        Cart inserted = captor.getValue();
        assertEquals(10L, inserted.getProductId());
        assertNull(inserted.getSpecId());
        assertEquals(2, inserted.getQuantity());
        assertEquals(1, inserted.getSelected());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("有规格商品按规格维度合并")
    void addToCartWithSpecMergesBySpec() {
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 100));
        ProductSpec spec = new ProductSpec();
        spec.setId(20L);
        spec.setProductId(10L);
        spec.setSpecName("5斤装");
        spec.setPrice(new BigDecimal("35.00"));
        spec.setStock(50);
        when(productSpecMapper.selectById(20L)).thenReturn(spec);

        Cart exist = buildCart(2L, 10L, 20L, 1, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        CartAddRequest request = new CartAddRequest();
        request.setProductId(10L);
        request.setSpecId(20L);
        request.setQuantity(4);
        cartService.addToCart(request);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).updateById(captor.capture());
        assertEquals(2L, captor.getValue().getId());
        assertEquals(5, captor.getValue().getQuantity());
    }

    @Test
    @DisplayName("加购超库存报库存不足")
    void addToCartOverStockFails() {
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 2));
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        CartAddRequest request = new CartAddRequest();
        request.setProductId(10L);
        request.setQuantity(5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.addToCart(request));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ==================== C3：修改数量时校验商品状态 ====================

    @Test
    @DisplayName("下架商品修改数量被拒绝")
    void updateQuantityOnOffShelfProductFails() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildCart(1L, 10L, null, 1, 1));
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 0, 100));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setQuantity(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.updateQuantity(1L, request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("修改数量超库存报库存不足")
    void updateQuantityOverStockFails() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildCart(1L, 10L, null, 1, 1));
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 2));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setQuantity(9);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.updateQuantity(1L, request));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常修改数量更新成功")
    void updateQuantitySuccess() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildCart(1L, 10L, null, 1, 1));
        when(productMapper.selectById(10L)).thenReturn(buildProduct(10L, 1, 100));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setQuantity(4);
        cartService.updateQuantity(1L, request);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).updateById(captor.capture());
        assertEquals(4, captor.getValue().getQuantity());
    }

    // ==================== C1：失效商品标记与结算过滤 ====================

    @Test
    @DisplayName("购物车列表标记下架商品为失效")
    void listMarksOffShelfAsInvalid() {
        when(cartMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildCart(1L, 10L, null, 2, 1)));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(buildProduct(10L, 0, 100)));

        List<CartItemVO> result = cartService.listCartItems();
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getValid());
        assertEquals("商品已下架", result.get(0).getInvalidReason());
        assertEquals("赣南脐橙", result.get(0).getProductName());
    }

    @Test
    @DisplayName("购物车列表标记已删除商品为失效")
    void listMarksDeletedAsInvalid() {
        when(cartMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildCart(1L, 99L, null, 2, 1)));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of());

        List<CartItemVO> result = cartService.listCartItems();
        assertEquals(0, result.get(0).getValid());
        assertEquals("商品已删除", result.get(0).getInvalidReason());
    }

    @Test
    @DisplayName("正常商品返回有效与小计")
    void listReturnsValidWithSubtotal() {
        when(cartMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildCart(1L, 10L, null, 2, 1)));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(buildProduct(10L, 1, 100)));

        List<CartItemVO> result = cartService.listCartItems();
        CartItemVO vo = result.get(0);
        assertEquals(1, vo.getValid());
        assertEquals(new BigDecimal("29.90"), vo.getPrice());
        assertEquals(0, new BigDecimal("59.80").compareTo(vo.getSubtotal()));
    }

    @Test
    @DisplayName("结算列表自动过滤失效商品")
    void selectedItemsFilterInvalid() {
        Cart valid = buildCart(1L, 10L, null, 2, 1);
        Cart invalid = buildCart(2L, 11L, null, 1, 1);
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(valid, invalid));
        Product p10 = buildProduct(10L, 1, 100);
        Product p11 = buildProduct(11L, 0, 50);
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(p10, p11));

        List<CartItemVO> result = cartService.listSelectedItems();
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getProductId());
    }

    // ==================== C2：批量转换不遗漏规格覆盖 ====================

    @Test
    @DisplayName("带规格商品价格与库存以规格为准")
    void listUsesSpecPriceAndStock() {
        Cart cart = buildCart(1L, 10L, 20L, 1, 1);
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cart));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(buildProduct(10L, 1, 100)));
        ProductSpec spec = new ProductSpec();
        spec.setId(20L);
        spec.setProductId(10L);
        spec.setSpecName("5斤装");
        spec.setPrice(new BigDecimal("35.00"));
        spec.setStock(50);
        when(productSpecMapper.selectBatchIds(anyList())).thenReturn(List.of(spec));

        CartItemVO vo = cartService.listCartItems().get(0);
        assertEquals("5斤装", vo.getSpecName());
        assertEquals(new BigDecimal("35.00"), vo.getPrice());
        assertEquals(50, vo.getStock());
        assertEquals(0, new BigDecimal("35.00").compareTo(vo.getSubtotal()));
    }
}
