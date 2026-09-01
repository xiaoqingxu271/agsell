package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ProductListRequest;
import com.lichun.agsell.model.vo.CategoryListItemVO;
import com.lichun.agsell.model.vo.ProductDetailVO;
import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.service.ProductCategoryService;
import com.lichun.agsell.service.ProductService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户端-商品接口", description = "商品列表、详情、热销、新品")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryService categoryService;

    @Operation(summary = "分类列表")
    @GetMapping("/category/list")
    public BaseResponse<List<CategoryListItemVO>> listCategories() {
        return ResultUtils.success(categoryService.listCategories());
    }

    @Operation(summary = "商品列表（分页+筛选+排序）")
    @GetMapping("/list")
    public BaseResponse<Page<ProductListItemVO>> listProducts(ProductListRequest request) {
        return ResultUtils.success(productService.listProducts(request));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/detail/{id}")
    public BaseResponse<ProductDetailVO> getProductDetail(@PathVariable Long id) {
        return ResultUtils.success(productService.getProductDetail(id));
    }

    @Operation(summary = "热销商品")
    @GetMapping("/hot")
    public BaseResponse<List<ProductListItemVO>> listHotProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(productService.listHotProducts(limit));
    }

    @Operation(summary = "新品推荐")
    @GetMapping("/new")
    public BaseResponse<List<ProductListItemVO>> listNewProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(productService.listNewProducts(limit));
    }
}
