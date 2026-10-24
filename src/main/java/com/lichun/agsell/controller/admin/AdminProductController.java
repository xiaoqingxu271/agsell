package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.dto.ProductBatchStatusRequest;
import com.lichun.agsell.model.dto.ProductQueryRequest;
import com.lichun.agsell.model.dto.ProductQuickEditRequest;
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
    @OperationLog(module = "商品管理", action = "商品上下架",
            content = "商品 #{#id} 已#{#status == 1 ? '上架' : '下架'}")
    public BaseResponse<Void> updateProductStatus(@PathVariable Long id,
                                                   @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return ResultUtils.success(null);
    }

    @Operation(summary = "批量上架/下架（列表多选）")
    @PostMapping("/batch")
    @OperationLog(module = "商品管理", action = "商品批量上下架",
            content = "批量#{#request.status == 1 ? '上架' : '下架'} #{#request.ids?.size()} 个商品")
    public BaseResponse<Integer> batchUpdateStatus(@RequestBody ProductBatchStatusRequest request) {
        return ResultUtils.success(productService.batchUpdateStatus(request.getIds(), request.getStatus()));
    }

    @Operation(summary = "快速编辑价格/库存（列表内联，只改传了的字段）")
    @PutMapping("/{id}/quick-edit")
    @OperationLog(module = "商品管理", action = "商品快速编辑",
            content = "商品 #{#id} 快速编辑价格/库存")
    public BaseResponse<Void> quickEdit(@PathVariable Long id,
                                        @RequestBody ProductQuickEditRequest request) {
        productService.quickEditPriceStock(id, request.getPrice(), request.getStock());
        return ResultUtils.success(null);
    }
}
