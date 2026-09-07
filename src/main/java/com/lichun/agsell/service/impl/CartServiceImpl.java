package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    private final CartMapper cartMapper;
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

        // 查询是否已存在（无规格商品匹配 spec_id IS NULL，避免重复入车）
        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, request.getProductId())
                .eq(spec != null, Cart::getSpecId, request.getSpecId())
                .isNull(spec == null, Cart::getSpecId));

        if (exist != null) {
            // 累加数量
            int totalQuantity = exist.getQuantity() + newQuantity;
            ThrowUtils.throwIf(totalQuantity > availableStock,
                    ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");
            Cart update = new Cart();
            update.setId(exist.getId());
            update.setQuantity(totalQuantity);
            cartMapper.updateById(update);
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
            cartMapper.insert(cart);
        }
    }

    @Override
    public List<CartItemVO> listCartItems() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));

        return convertToVOs(carts);
    }

    @Override
    @Transactional
    public void updateQuantity(Long id, CartUpdateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getQuantity() == null || request.getQuantity() <= 0,
                ErrorCode.PARAMS_ERROR, "数量必须大于0");

        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId));
        ThrowUtils.throwIf(cart == null, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        // 校验库存与商品状态
        Product product = productMapper.selectById(cart.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在或已删除");
        ThrowUtils.throwIf(product.getStatus() != 1, ErrorCode.OPERATION_ERROR, "商品已下架，请删除后重新选购");

        ProductSpec spec = cart.getSpecId() != null
                ? productSpecMapper.selectById(cart.getSpecId()) : null;
        int availableStock = spec != null ? spec.getStock() : product.getStock();
        ThrowUtils.throwIf(request.getQuantity() > availableStock,
                ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");

        Cart update = new Cart();
        update.setId(id);
        update.setQuantity(request.getQuantity());
        cartMapper.updateById(update);
    }

    @Override
    public void deleteCartItem(Long id) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        boolean exists = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)) > 0;
        ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        cartMapper.deleteById(id);
    }

    @Override
    public void toggleSelect(Long id) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId));
        ThrowUtils.throwIf(cart == null, ErrorCode.NOT_FOUND_ERROR, "购物车商品不存在");

        Cart update = new Cart();
        update.setId(id);
        update.setSelected(cart.getSelected() == 1 ? 0 : 1);
        cartMapper.updateById(update);
    }

    @Override
    public void selectAll(boolean selected) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        cartMapper.update(null, new LambdaUpdateWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .set(Cart::getSelected, selected ? 1 : 0));
    }

    @Override
    public void batchDelete(List<Long> ids) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(ids == null || ids.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要删除的商品");

        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, ids));
    }

    @Override
    public List<CartItemVO> listSelectedItems() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getSelected, 1));

        // 过滤失效商品（已下架/已删除），避免结算时携带无效条目
        return convertToVOs(carts).stream()
                .filter(vo -> vo.getValid() != null && vo.getValid() == 1)
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 批量转换购物车条目，商品/规格一次查出（消除 N+1），并标记失效商品
     */
    private List<CartItemVO> convertToVOs(List<Cart> carts) {
        if (carts.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = carts.stream()
                .map(Cart::getProductId)
                .distinct()
                .collect(Collectors.toList());
        List<Long> specIds = carts.stream()
                .map(Cart::getSpecId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, ProductSpec> specMap = specIds.isEmpty() ? Map.of()
                : productSpecMapper.selectBatchIds(specIds).stream()
                        .collect(Collectors.toMap(ProductSpec::getId, s -> s, (a, b) -> a));

        return carts.stream()
                .map(cart -> convertToVO(cart,
                        productMap.get(cart.getProductId()),
                        cart.getSpecId() != null ? specMap.get(cart.getSpecId()) : null))
                .collect(Collectors.toList());
    }

    private CartItemVO convertToVO(Cart cart, Product product, ProductSpec spec) {
        CartItemVO vo = new CartItemVO();
        vo.setId(cart.getId());
        vo.setProductId(cart.getProductId());
        vo.setSpecId(cart.getSpecId());
        vo.setQuantity(cart.getQuantity());
        vo.setSelected(cart.getSelected());
        vo.setCreateTime(cart.getCreateTime());

        // 失效商品标记：删除/下架的商品不参与结算，前端置灰展示
        if (product == null) {
            vo.setValid(0);
            vo.setInvalidReason("商品已删除");
            return vo;
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            vo.setValid(0);
            vo.setInvalidReason("商品已下架");
            vo.setProductName(product.getName());
            vo.setProductImage(product.getMainImage());
            return vo;
        }
        vo.setValid(1);
        vo.setProductName(product.getName());
        vo.setProductImage(product.getMainImage());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());

        // 规格信息
        if (spec != null) {
            vo.setSpecName(spec.getSpecName());
            vo.setPrice(spec.getPrice());
            vo.setStock(spec.getStock());
        }

        // 计算小计
        if (vo.getPrice() != null && vo.getQuantity() != null) {
            vo.setSubtotal(vo.getPrice().multiply(BigDecimal.valueOf(vo.getQuantity())));
        }

        return vo;
    }
}
