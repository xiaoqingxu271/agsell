package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.CategoryCreateRequest;
import com.lichun.agsell.model.vo.CategoryListItemVO;
import com.lichun.agsell.model.vo.CategoryTreeVO;

import java.util.List;

public interface ProductCategoryService {

    /**
     * 获取分类树（用于管理端）
     */
    List<CategoryTreeVO> getCategoryTree();

    /**
     * 获取分类列表
     */
    List<CategoryListItemVO> listCategories();

    /**
     * 新增或更新分类
     */
    void saveOrUpdateCategory(CategoryCreateRequest request);

    /**
     * 删除分类（检查是否有子分类关联）
     */
    void deleteCategory(Long id);

    /**
     * 更新分类状态
     */
    void updateCategoryStatus(Long id, Integer status);
}
