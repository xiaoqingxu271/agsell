package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.model.dto.CategoryCreateRequest;
import com.lichun.agsell.model.entity.ProductCategory;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.vo.CategoryListItemVO;
import com.lichun.agsell.model.vo.CategoryTreeVO;
import com.lichun.agsell.service.ProductCategoryService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements ProductCategoryService {

    private final ProductMapper productMapper;

    @Override
    public List<CategoryTreeVO> getCategoryTree() {
        List<ProductCategory> allCategories = lambdaQuery()
                .eq(ProductCategory::getStatus, 1)
                .orderByDesc(ProductCategory::getSort)
                .orderByAsc(ProductCategory::getId)
                .list();

        Map<Long, CategoryTreeVO> nodeMap = new LinkedHashMap<>();
        for (ProductCategory cat : allCategories) {
            nodeMap.put(cat.getId(), convertToVO(cat));
        }

        List<CategoryTreeVO> rootNodes = new ArrayList<>();
        for (ProductCategory cat : allCategories) {
            CategoryTreeVO node = nodeMap.get(cat.getId());
            if (cat.getParentId() == null || cat.getParentId() == 0) {
                rootNodes.add(node);
            } else {
                CategoryTreeVO parent = nodeMap.get(cat.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    rootNodes.add(node);
                }
            }
        }

        return rootNodes;
    }

    @Override
    public List<CategoryListItemVO> listCategories() {
        return lambdaQuery()
                .eq(ProductCategory::getStatus, 1)
                .orderByDesc(ProductCategory::getSort)
                .list()
                .stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());
    }

    @Override
    public void saveOrUpdateCategory(CategoryCreateRequest request) {
        ThrowUtils.throwIf(StrUtil.isBlank(request.getName()),
                ErrorCode.PARAMS_ERROR, "分类名称不能为空");
        ThrowUtils.throwIf(request.getName().length() > 20,
                ErrorCode.PARAMS_ERROR, "分类名称不能超过20个字符");

        Long parentId = request.getParentId() != null ? request.getParentId() : 0L;

        if (request.getId() != null) {
            ThrowUtils.throwIf(parentId.equals(request.getId()),
                    ErrorCode.PARAMS_ERROR, "不能将自身设为父分类");
            ThrowUtils.throwIf(isDescendant(request.getId(), parentId),
                    ErrorCode.PARAMS_ERROR, "不能将子分类设为父分类");

            ProductCategory update = new ProductCategory();
            update.setId(request.getId());
            update.setName(request.getName());
            update.setIcon(request.getIcon());
            update.setParentId(parentId);
            update.setSort(request.getSort() != null ? request.getSort() : 0);
            updateById(update);
        } else {
            ProductCategory category = new ProductCategory();
            category.setName(request.getName());
            category.setIcon(request.getIcon());
            category.setParentId(parentId);
            category.setSort(request.getSort() != null ? request.getSort() : 0);
            category.setStatus(1);
            save(category);
        }
    }

    @Override
    public void deleteCategory(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "分类ID不能为空");

        ProductCategory exist = getById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "分类不存在");

        long childCount = lambdaQuery()
                .eq(ProductCategory::getParentId, id)
                .count();
        ThrowUtils.throwIf(childCount > 0, ErrorCode.OPERATION_ERROR, "该分类下存在子分类，无法删除");

        long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getCategoryId, id));
        ThrowUtils.throwIf(productCount > 0, ErrorCode.OPERATION_ERROR, "该分类下有商品关联，无法删除");

        removeById(id);
    }

    @Override
    public void updateCategoryStatus(Long id, Integer status) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "分类ID不能为空");
        ThrowUtils.throwIf(status == null, ErrorCode.PARAMS_ERROR, "状态不能为空");
        ThrowUtils.throwIf(status != 0 && status != 1,
                ErrorCode.PARAMS_ERROR, "状态值不合法");

        ProductCategory exist = getById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "分类不存在");

        ProductCategory update = new ProductCategory();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }

    private CategoryTreeVO convertToVO(ProductCategory category) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSort());
        vo.setCreateTime(category.getCreateTime());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    private CategoryListItemVO convertToListVO(ProductCategory category) {
        CategoryListItemVO vo = new CategoryListItemVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setCreateTime(category.getCreateTime());
        return vo;
    }

    private boolean isDescendant(Long id, Long parentId) {
        if (parentId == null || parentId == 0) return false;
        ProductCategory parent = getById(parentId);
        if (parent == null) return false;
        if (parent.getId().equals(id)) return true;
        if (parent.getParentId() == null || parent.getParentId() == 0) return false;
        return isDescendant(id, parent.getParentId());
    }
}
