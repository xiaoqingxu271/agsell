package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.service.PaymentService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Map<Integer, String> STATUS_TEXT_MAP = Map.of(
            0, "待付款",
            1, "已支付",
            2, "待收货",
            3, "已完成",
            4, "已取消",
            5, "售后处理中",
            6, "已退款"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    @Override
    @Transactional
    public void createPayment(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != 0,
                ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法支付");

        // 1. 扣减库存（乐观锁：stock >= quantity 才允许扣减，防止超卖）
        // 库存口径：有规格商品扣减规格库存，无规格商品扣减商品总库存；
        // 任一条目扣减失败则抛出异常，整个支付事务回滚（订单状态不变）。
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            if (item.getSpecId() != null) {
                int specRows = productSpecMapper.update(null, new LambdaUpdateWrapper<ProductSpec>()
                        .setSql("stock = stock - " + item.getQuantity())
                        .eq(ProductSpec::getId, item.getSpecId())
                        .ge(ProductSpec::getStock, item.getQuantity()));
                if (specRows == 0) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                            String.format("商品 %s 库存不足", item.getProductName()));
                }
            } else {
                int prodRows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .setSql("stock = stock - " + item.getQuantity())
                        .eq(Product::getId, item.getProductId())
                        .ge(Product::getStock, item.getQuantity()));
                if (prodRows == 0) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                            String.format("商品 %s 库存不足", item.getProductName()));
                }
            }
        }

        // 2. 更新订单状态为已支付（待发货）
        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(1); // 待发货
        update.setPayType(1); // 模拟支付
        update.setPayTime(LocalDateTime.now());
        orderMapper.updateById(update);
    }

    @Override
    public PaymentStatusVO getPaymentStatus(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        PaymentStatusVO vo = new PaymentStatusVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(order.getStatus(), "未知"));
        vo.setPayTime(order.getPayTime());
        return vo;
    }
}
