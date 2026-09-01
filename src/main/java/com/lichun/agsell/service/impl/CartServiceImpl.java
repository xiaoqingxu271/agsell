package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CartMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.dto.CartAddRequest;
import com.lichun.agsell.model.dto.CartUpdateRequest;
import com.lichun.agsell.model.entity.Cart;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.model.vo.CartItemVO;
import com.lichun.agsell.service.CartService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    @Override
    @Transactional
    public void addToCart(CartAddRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getProductId() == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        ThrowUtils.throwIf(request.getQuantity() == null || request.getQuantity() <= 0,
                ErrorCode.PARAMS_ERROR, "数量必须大于0");

        // 校验商品
        Product product = productMapper.selectById(request.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");
        ThrowUtils.throwIf(product.getStatus() != 1, ErrorCode.NOT_FOUND_ERROR, "商品已下架");

        // 校验规格
        ProductSpec spec = null;
        if (request.getSpecId() != null) {
            spec = productSpecMapper.selectById(request.getSpecId());
            ThrowUtils.throwIf(spec == null, ErrorCode.NOT_FOUND_ERROR, "规格不存在");
            ThrowUtils.throwIf(!spec.getProductId().equals(request.getProductId()),
                    ErrorCode.PARAMS_ERROR, "规格与商品不匹配");
        }

        // 校验库存
        int availableStock = spec != null ? spec.getStock() : product.getStock();
        int newQuantity = request.getQuantity();

        // 查询是否已存在
        Cart exist = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, request.getProductId())
                .eq(spec != null, Cart::getSpecId, request.getSpecId())
                .ne(spec == null, Cart::getSpecId, (Object) null)
                .one();

        if (exist != null) {
            // 累加数量
            int totalQuantity = exist.getQuantity() + newQuantity;
            ThrowUtils.throwIf(totalQuantity > availableStock,
                    ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");
            Cart update = new Cart();
            update.setId(exist.getId());
            update.setQuantity(totalQuantity);
            updateById(update);
        } else {
            // 新增
            ThrowUtils.throwIf(newQuantity > availableStock,
                    ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(request.getProductId());
            cart.setSpecId(request.getSpecId());
            cart.setQuantity(newQuantity);
            cart.setSelected(1);
            save(cart);
        }
    }

    @Override
    public List<CartItemVO> listCartItems() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        List<Cart> carts = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime)
                .list();

        return carts.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateQuantity(Long id, CartUpdateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getQuantity() == null || request.getQuantity() <= 0,
                ErrorCode.PARAMS_ERROR, "数量必须大于0");

        Cart cart = lambdaQuery()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .one();
        ThrowUtils.throwIf(cart == null, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        // 校验库存
        Product product = productMapper.selectById(cart.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");

        ProductSpec spec = cart.getSpecId() != null
                ? productSpecMapper.selectById(cart.getSpecId()) : null;
        int availableStock = spec != null ? spec.getStock() : product.getStock();
        ThrowUtils.throwIf(request.getQuantity() > availableStock,
                ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");

        Cart update = new Cart();
        update.setId(id);
        update.setQuantity(request.getQuantity());
        updateById(update);
    }

    @Override
    public void deleteCartItem(Long id) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        boolean exists = lambdaQuery()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .exists();
        ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        removeById(id);
    }

    @Override
    public void toggleSelect(Long id) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Cart cart = lambdaQuery()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .one();
        ThrowUtils.throwIf(cart == null, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        Cart update = new Cart();
        update.setId(id);
        update.setSelected(cart.getSelected() == 1 ? 0 : 1);
        updateById(update);
    }

    @Override
    public void selectAll(boolean selected) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        lambdaUpdate()
                .eq(Cart::getUserId, userId)
                .set(Cart::getSelected, selected ? 1 : 0)
                .update();
    }

    @Override
    public void batchDelete(List<Long> ids) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(ids == null || ids.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要删除的商品");

        lambdaUpdate()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, ids)
                .remove();
    }

    @Override
    public List<CartItemVO> listSelectedItems() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        List<Cart> carts = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getSelected, 1)
                .list();

        return carts.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private CartItemVO convertToVO(Cart cart) {
        CartItemVO vo = new CartItemVO();
        vo.setId(cart.getId());
        vo.setProductId(cart.getProductId());
        vo.setSpecId(cart.getSpecId());
        vo.setQuantity(cart.getQuantity());
        vo.setSelected(cart.getSelected());
        vo.setCreateTime(cart.getCreateTime());

        // 查询商品信息
        Product product = productMapper.selectById(cart.getProductId());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getMainImage());
            vo.setPrice(product.getPrice());
            vo.setStock(product.getStock());
        }

        // 查询规格信息
        if (cart.getSpecId() != null) {
            ProductSpec spec = productSpecMapper.selectById(cart.getSpecId());
            if (spec != null) {
                vo.setSpecName(spec.getSpecName());
                vo.setPrice(spec.getPrice());
                vo.setStock(spec.getStock());
            }
        }

        // 计算小计
        if (vo.getPrice() != null && vo.getQuantity() != null) {
            vo.setSubtotal(vo.getPrice().multiply(BigDecimal.valueOf(vo.getQuantity())));
        }

        return vo;
    }
}
