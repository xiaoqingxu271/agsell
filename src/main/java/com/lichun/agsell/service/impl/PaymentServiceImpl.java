package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.service.PaymentService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
            5, "售后中"
    );

    private final OrderMapper orderMapper;

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
