package com.lichun.agsell.controller.admin;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.CategoryCreateRequest;
import com.lichun.agsell.model.vo.CategoryListItemVO;
import com.lichun.agsell.model.vo.CategoryTreeVO;
import com.lichun.agsell.service.ProductCategoryService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-分类管理", description = "商品分类增删改查、状态管理")
@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ProductCategoryService categoryService;

    @Operation(summary = "分类树（管理端）")
    @GetMapping("/tree")
    public BaseResponse<List<CategoryTreeVO>> getCategoryTree() {
        return ResultUtils.success(categoryService.getCategoryTree());
    }

    @Operation(summary = "分类列表")
    @GetMapping("/list")
    public BaseResponse<List<CategoryListItemVO>> listCategories() {
        return ResultUtils.success(categoryService.listCategories());
    }

    @Operation(summary = "新增/编辑分类")
    @PostMapping
    public BaseResponse<Void> saveOrUpdateCategory(@RequestBody CategoryCreateRequest request) {
        categoryService.saveOrUpdateCategory(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "批量保存排序（拖拽排序后提交）")
    @PutMapping("/sort")
    @com.lichun.agsell.annotation.OperationLog(module = "分类管理", action = "分类排序",
            content = "拖拽调整分类排序 #{#request.items?.size()} 项")
    public BaseResponse<Integer> saveSort(@RequestBody com.lichun.agsell.model.dto.CategorySortRequest request) {
        return ResultUtils.success(categoryService.saveSort(request.getItems()));
    }

    @Operation(summary = "更新分类状态")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateCategoryStatus(@PathVariable Long id,
                                                    @RequestParam Integer status) {
        categoryService.updateCategoryStatus(id, status);
        return ResultUtils.success(null);
    }
}
