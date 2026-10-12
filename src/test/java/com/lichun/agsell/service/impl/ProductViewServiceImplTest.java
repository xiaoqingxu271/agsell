package com.lichun.agsell.service.impl;

import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.UserProductViewMapper;
import com.lichun.agsell.model.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 浏览足迹落库边界测试：游客跳过、非在售商品跳过、埋点异常不外抛
 */
@ExtendWith(MockitoExtension.class)
class ProductViewServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private UserProductViewMapper viewMapper;

    @InjectMocks
    private ProductViewServiceImpl service;

    private Product onSaleProduct(Long categoryId) {
        Product p = new Product();
        p.setId(100L);
        p.setCategoryId(categoryId);
        p.setStatus(1);
        return p;
    }

    @Test
    @DisplayName("游客（userId 为 null）：不查商品不写足迹")
    void guestSkipped() {
        service.recordView(null, 100L);
        verifyNoInteractions(productMapper, viewMapper);
    }

    @Test
    @DisplayName("商品ID为空：不查商品不写足迹")
    void nullProductIdSkipped() {
        service.recordView(1L, null);
        verifyNoInteractions(productMapper, viewMapper);
    }

    @Test
    @DisplayName("商品不存在/已下架/缺类目：均不写足迹")
    void invalidProductSkipped() {
        Product offSale = onSaleProduct(9L);
        offSale.setStatus(0);
        when(productMapper.selectById(1L)).thenReturn(null);
        when(productMapper.selectById(3L)).thenReturn(onSaleProduct(null));
        when(productMapper.selectById(4L)).thenReturn(offSale);

        service.recordView(1L, 1L);
        service.recordView(1L, 3L);
        service.recordView(1L, 4L);

        verifyNoInteractions(viewMapper);
    }

    @Test
    @DisplayName("在售商品：按 用户×商品×类目 upsert 一条足迹")
    void onSaleProductUpserts() {
        when(productMapper.selectById(100L)).thenReturn(onSaleProduct(2109L));

        service.recordView(7L, 100L);

        verify(viewMapper).upsertDailyView(anyLong(), eq(7L), eq(100L), eq(2109L));
    }

    @Test
    @DisplayName("落库异常（如唯一索引/DB 故障）：只记日志，绝不影响用户看商品")
    void dbFailureSwallowed() {
        when(productMapper.selectById(100L)).thenReturn(onSaleProduct(2109L));
        when(viewMapper.upsertDailyView(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Duplicate entry"));

        assertDoesNotThrow(() -> service.recordView(7L, 100L));
    }
}
