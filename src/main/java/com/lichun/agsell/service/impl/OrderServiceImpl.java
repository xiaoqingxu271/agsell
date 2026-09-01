package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CartMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.OrderCreateRequest;
import com.lichun.agsell.model.entity.*;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.model.vo.OrderListItemVO;
import com.lichun.agsell.service.OrderService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Map<String, String> STATUS_TEXT_MAP = Map.of(
            "0", "待付款",
            "1", "待发货",
            "2", "待收货",
            "3", "已完成",
            "4", "已取消",
            "5", "售后中"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final SysUserAddressMapper addressMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional
    public OrderCreateVO createOrder(OrderCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getAddressId() == null, ErrorCode.PARAMS_ERROR, "请选择收货地址");
        ThrowUtils.throwIf(request.getCartItemIds() == null || request.getCartItemIds().isEmpty(),
                ErrorCode.PARAMS_ERROR, "请选择要结算的商品");

        // 1. 校验收货地址
        SysUserAddress address = addressMapper.selectById(request.getAddressId());
        ThrowUtils.throwIf(address == null || !address.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "地址不存在");

        // 2. 查询购物车选中商品
        List<Cart> cartItems = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getSelected, 1)
                .in(Cart::getId, request.getCartItemIds()));
        ThrowUtils.throwIf(cartItems.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要结算的商品");

        // 3. 校验商品库存并计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (Cart cartItem : cartItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            ThrowUtils.throwIf(product == null || product.getStatus() != 1,
                    ErrorCode.NOT_FOUND_ERROR, "商品已下架或不存在: " + cartItem.getProductId());

            ProductSpec spec = cartItem.getSpecId() != null
                    ? productSpecMapper.selectById(cartItem.getSpecId()) : null;
            if (cartItem.getSpecId() != null && spec == null) {
                ThrowUtils.throwIf(true, ErrorCode.NOT_FOUND_ERROR, "规格不存在");
            }

            // 校验库存
            int availableStock = spec != null ? spec.getStock() : product.getStock();
            ThrowUtils.throwIf(cartItem.getQuantity() > availableStock,
                    ErrorCode.STOCK_INSUFFICIENT, String.format("商品 %s 库存不足", product.getName()));

            BigDecimal price = spec != null ? spec.getPrice() : product.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            // 构建订单明细快照
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setSpecName(spec != null ? spec.getSpecName() : null);
            item.setPrice(price);
            item.setQuantity(cartItem.getQuantity());
            item.setSubtotal(subtotal);
            orderItems.add(item);
        }

        // 4. 生成订单
        String orderNo = generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setFreight(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setPayAmount(totalAmount);
        order.setStatus(0); // 待付款
        order.setAddressId(address.getId());
        order.setReceiver(address.getReceiver());
        order.setPhone(address.getPhone());
        order.setAddress(address.getProvince() + address.getCity()
                + address.getDistrict() + address.getDetail());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        // 5. 保存订单明细
        orderItems.forEach(item -> item.setOrderId(order.getId()));
        orderItemMapper.insert(orderItems);

        // 6. 删除购物车中选中的商品
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, request.getCartItemIds()));

        // 7. 返回订单信息
        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(orderNo);
        vo.setPayAmount(totalAmount);
        vo.setTotalAmount(totalAmount);
        vo.setStatus(0);
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    @Override
    public Page<OrderListItemVO> listOrders(int pageNum, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<OrderListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(order -> {
            OrderListItemVO vo = new OrderListItemVO();
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(STATUS_TEXT_MAP.get(String.valueOf(order.getStatus())));
            vo.setCreateTime(order.getCreateTime());
            // 查询商品数量
            long itemCount = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            vo.setItemCount((int) itemCount);
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public OrderDetailVO getOrderDetail(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreight(order.getFreight());
        vo.setDiscount(order.getDiscount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.get(String.valueOf(order.getStatus())));
        vo.setReceiver(order.getReceiver());
        vo.setPhone(order.getPhone());
        vo.setAddress(order.getAddress());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setLogType(order.getLogType());
        vo.setLogNo(order.getLogNo());
        vo.setCancelReason(order.getCancelReason());

        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        vo.setItems(items.stream().map(item -> {
            OrderDetailVO.OrderItemVO itemVo = new OrderDetailVO.OrderItemVO();
            itemVo.setProductId(item.getProductId());
            itemVo.setProductName(item.getProductName());
            itemVo.setProductImage(item.getProductImage());
            itemVo.setSpecName(item.getSpecName());
            itemVo.setPrice(item.getPrice());
            itemVo.setQuantity(item.getQuantity());
            itemVo.setSubtotal(item.getSubtotal());
            return itemVo;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional
    public void cancelOrder(String orderNo, String reason) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != 0,
                ErrorCode.ORDER_STATUS_ERROR, "只有待付款订单可以取消");

        // 恢复库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product != null) {
                Product prodUpdate = new Product();
                prodUpdate.setId(product.getId());
                prodUpdate.setStock(product.getStock() + item.getQuantity());
                productMapper.updateById(prodUpdate);
            }
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(4); // 已取消
        update.setCancelReason(reason);
        orderMapper.updateById(update);
    }

    @Override
    @Transactional
    public void confirmReceive(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != 2,
                ErrorCode.ORDER_STATUS_ERROR, "只有待收货订单可以确认收货");

        // 更新订单状态
        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(3); // 已完成
        update.setReceiveTime(LocalDateTime.now());
        orderMapper.updateById(update);

        // 扣减库存并增加销量
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product != null) {
                Product prodUpdate = new Product();
                prodUpdate.setId(product.getId());
                prodUpdate.setStock(product.getStock() - item.getQuantity());
                prodUpdate.setSales(product.getSales() + item.getQuantity());
                productMapper.updateById(prodUpdate);
            }
        }
    }

    // ==================== 私有方法 ====================

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int random = new Random().nextInt(1000000);
        return String.format("AGS%s%06d", timestamp, random);
    }
}
