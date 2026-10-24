package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.FavoriteMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.entity.Favorite;
import com.lichun.agsell.model.entity.Product;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收藏服务：加/取消幂等、重复收藏走唯一键兜底、列表批量取商品快照（N+1 防护）。
 * 收藏同时是 AI 偏好的一路信号，但 Java 侧只管 CRUD，不做任何联动。
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private ProductMapper productMapper;

    private FavoriteServiceImpl favoriteService;

    private static final Long USER_ID = 9L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Favorite.class);
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteServiceImpl(favoriteMapper, productMapper);
        BaseContext.setCurrentId(USER_ID, "jti-test");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private Product product(long id, int status) {
        Product p = new Product();
        p.setId(id);
        p.setName("赣南脐橙 新鲜当季");
        p.setMainImage("https://cdn/1.jpg");
        p.setPrice(new BigDecimal("39.90"));
        p.setOriginalPrice(new BigDecimal("59.90"));
        p.setSales(1280);
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("收藏成功：写入当前用户与商品")
    void addInsertsFavorite() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1));
        when(favoriteMapper.insert(any(Favorite.class))).thenReturn(1);

        favoriteService.add(1L);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(1L, captor.getValue().getProductId());
    }

    @Test
    @DisplayName("重复收藏（唯一键冲突）按幂等成功处理，不报错")
    void addDuplicateTreatedAsSuccess() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1));
        when(favoriteMapper.insert(any(Favorite.class)))
                .thenThrow(new DuplicateKeyException("uk_user_product"));

        favoriteService.add(1L);  // 不应抛出
    }

    @Test
    @DisplayName("收藏不存在的商品报 NOT_FOUND")
    void addMissingProductFails() {
        when(productMapper.selectById(404L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> favoriteService.add(404L));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        verify(favoriteMapper, never()).insert(any(Favorite.class));
    }

    @Test
    @DisplayName("取消收藏：返回是否真的删掉了行")
    void removeReturnsDeletedRows() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(1);
        assertTrue(favoriteService.remove(1L));

        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(0);
        assertFalse(favoriteService.remove(2L));
    }

    @Test
    @DisplayName("收藏列表：批量取商品快照，已删除商品剔除不显示")
    void pageMineFiltersDeletedProducts() {
        Favorite f1 = new Favorite();
        f1.setId(100L);
        f1.setUserId(USER_ID);
        f1.setProductId(1L);
        Favorite f2 = new Favorite();
        f2.setId(101L);
        f2.setUserId(USER_ID);
        f2.setProductId(2L);

        Page<Favorite> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(f1, f2));
        when(favoriteMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(productMapper.selectList(any(Wrapper.class))).thenReturn(List.of(product(1L, 1)));

        Page<?> result = favoriteService.pageMine(1, 10);
        // 商品 2 已删除：列表剔除，总数保持收藏行数
        assertEquals(1, result.getRecords().size());
        assertEquals(2L, result.getTotal());
    }
}
