package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.CartAddRequest;
import com.lichun.agsell.model.dto.CartUpdateRequest;
import com.lichun.agsell.model.vo.CartItemVO;

import java.util.List;

public interface CartService {

    /**
     * 加入购物车
     */
    void addToCart(CartAddRequest request);

    /**
     * 获取购物车列表
     */
    List<CartItemVO> listCartItems();

    /**
     * 修改购物车数量
     */
    void updateQuantity(Long id, CartUpdateRequest request);

    /**
     * 删除购物车商品
     */
    void deleteCartItem(Long id);

    /**
     * 选中/取消选中
     */
    void toggleSelect(Long id);

    /**
     * 全选/取消全选
     */
    void selectAll(boolean selected);

    /**
     * 批量删除（结算后清空）
     */
    void batchDelete(List<Long> ids);

    /**
     * 获取选中的购物车条目
     */
    List<CartItemVO> listSelectedItems();
}
