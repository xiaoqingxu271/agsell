package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.dto.ProductQueryRequest;
import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.service.ProductService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-商品管理", description = "商品增删改查、状态管理")
@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表（分页+筛选）")
    @GetMapping("/list")
    public BaseResponse<Page<ProductListItemVO>> listProducts(ProductQueryRequest request) {
        return ResultUtils.success(productService.listProducts(request));
    }

    @Operation(summary = "新增/编辑商品")
    @PostMapping
    public BaseResponse<Void> saveOrUpdateProduct(@RequestBody ProductCreateRequest request) {
        productService.saveOrUpdateProduct(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "更新商品状态（上架/下架）")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateProductStatus(@PathVariable Long id,
                                                   @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return ResultUtils.success(null);
    }
}
