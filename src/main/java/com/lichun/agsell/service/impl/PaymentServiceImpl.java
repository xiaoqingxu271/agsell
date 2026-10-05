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
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.service.PaymentService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    @Override
    @Transactional
    public void createPayment(String orderNo, Integer payType) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        // 支付方式校验：1=支付宝 2=微信支付，缺省按支付宝处理
        int channel = payType == null ? PAY_TYPE_ALIPAY : payType;
        ThrowUtils.throwIf(channel != PAY_TYPE_ALIPAY && channel != PAY_TYPE_WECHAT,
                ErrorCode.PARAMS_ERROR, "不支持的支付方式");

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode(),
                ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法支付");

        // 1. 原子抢占支付权：条件 UPDATE（仅待付款→待发货生效）。
        //    并发重复支付（如双击支付按钮）只有一个事务能抢到，其余在此拒绝，
        //    避免旧实现"先查状态再无条件改状态"导致的重复扣库存。
        int claimed = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .set(Order::getStatus, OrderStatusEnum.PENDING_SHIPMENT.getCode()) // 待发货
                .set(Order::getPayType, channel) // 1=支付宝 2=微信支付
                .set(Order::getPayTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变化，请勿重复支付");
        }

        // 2. 扣减库存（乐观锁：stock >= quantity 才允许扣减，防止超卖）
        // 库存口径：有规格商品扣减规格库存，无规格商品扣减商品总库存；
        // 任一条目扣减失败则抛出异常，整个支付事务回滚（含步骤1的状态变更）。
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
        vo.setStatusText(OrderStatusEnum.textOf(order.getStatus()));
        vo.setPayTime(order.getPayTime());
        return vo;
    }
}
