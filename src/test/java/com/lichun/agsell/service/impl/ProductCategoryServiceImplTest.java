package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.model.dto.CategorySortRequest;
import com.lichun.agsell.model.entity.ProductCategory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类拖拽排序（P1 §5.4）：批量保存排序值的校验与落库。
 */
@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock
    private ProductCategoryMapper categoryMapper;

    private ProductCategoryServiceImpl categoryService;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductCategory.class);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // MP ServiceImpl 的 baseMapper 是泛型字段，@InjectMocks 按类型注入不可靠，
        // 用 ReflectionTestUtils 直接装配（getById/updateById 都走它）；
        // 构造器里的 ProductMapper 本测试用不到，给一个空 mock
        categoryService = new ProductCategoryServiceImpl(
                org.mockito.Mockito.mock(com.lichun.agsell.mapper.ProductMapper.class));
        ReflectionTestUtils.setField(categoryService, "baseMapper", categoryMapper);
    }

    private CategorySortRequest.SortItem item(Long id, Integer sort) {
        CategorySortRequest.SortItem item = new CategorySortRequest.SortItem();
        item.setId(id);
        item.setSort(sort);
        return item;
    }

    @Test
    @DisplayName("批量保存排序：逐项校验存在性后逐条更新")
    void saveSortUpdatesEachItem() {
        when(categoryMapper.selectById(1L)).thenReturn(new ProductCategory());
        when(categoryMapper.selectById(2L)).thenReturn(new ProductCategory());
        when(categoryMapper.updateById(any(ProductCategory.class))).thenReturn(1);

        int updated = categoryService.saveSort(List.of(item(1L, 30), item(2L, 20)));

        assertEquals(2, updated);
        ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(categoryMapper, times(2)).updateById(captor.capture());
        assertEquals(30, captor.getAllValues().get(0).getSort());
        assertEquals(20, captor.getAllValues().get(1).getSort());
    }

    @Test
    @DisplayName("排序项为空 / 缺字段 / 分类已不存在 → 拒绝且不落库")
    void saveSortRejectsBadInput() {
        assertThrows(BusinessException.class, () -> categoryService.saveSort(List.of()));
        assertThrows(BusinessException.class, () -> categoryService.saveSort(List.of(item(1L, null))));
        assertThrows(BusinessException.class, () -> categoryService.saveSort(List.of(item(null, 10))));

        when(categoryMapper.selectById(404L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.saveSort(List.of(item(404L, 10))));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());

        verify(categoryMapper, never()).updateById(any(ProductCategory.class));
    }
}
