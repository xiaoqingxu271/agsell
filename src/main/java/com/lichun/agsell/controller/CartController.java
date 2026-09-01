package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.CartAddRequest;
import com.lichun.agsell.model.dto.CartUpdateRequest;
import com.lichun.agsell.model.vo.CartItemVO;
import com.lichun.agsell.service.CartService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "购物车接口", description = "购物车增删改查、选中结算")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "加入购物车")
    @PostMapping("/add")
    public BaseResponse<Void> addToCart(@RequestBody CartAddRequest request) {
        cartService.addToCart(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "购物车列表")
    @GetMapping("/list")
    public BaseResponse<List<CartItemVO>> listCartItems() {
        return ResultUtils.success(cartService.listCartItems());
    }

    @Operation(summary = "修改购物车数量")
    @PutMapping("/item/{id}")
    public BaseResponse<Void> updateQuantity(@PathVariable Long id,
                                              @RequestBody CartUpdateRequest request) {
        cartService.updateQuantity(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除购物车商品")
    @DeleteMapping("/item/{id}")
    public BaseResponse<Void> deleteCartItem(@PathVariable Long id) {
        cartService.deleteCartItem(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "选中/取消选中")
    @PutMapping("/select/{id}")
    public BaseResponse<Void> toggleSelect(@PathVariable Long id) {
        cartService.toggleSelect(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "全选/取消全选")
    @PutMapping("/select-all")
    public BaseResponse<Void> selectAll(@RequestParam boolean selected) {
        cartService.selectAll(selected);
        return ResultUtils.success(null);
    }

    @Operation(summary = "批量删除（结算后清空）")
    @DeleteMapping("/batch")
    public BaseResponse<Void> batchDelete(@RequestBody List<Long> ids) {
        cartService.batchDelete(ids);
        return ResultUtils.success(null);
    }
}
