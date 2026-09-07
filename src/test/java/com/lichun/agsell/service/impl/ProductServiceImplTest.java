package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private ProductServiceImpl productService;

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
    }
}
