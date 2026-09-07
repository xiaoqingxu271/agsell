package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.AdminStatisticsVO;
import com.lichun.agsell.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 管理端数据统计实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsServiceImpl implements AdminStatisticsService {

    /** 已支付订单状态：待发货/待收货/已完成/售后中（排除待付款0、已取消4） */
    private static final List<Integer> PAID_STATUSES = List.of(1, 2, 3, 5);

    private final SysUserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    @Override
    public AdminStatisticsVO getOverview() {
        AdminStatisticsVO vo = new AdminStatisticsVO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 用户统计
        vo.setUserTotal(userMapper.selectCount(null));
        vo.setTodayNewUsers(userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().ge(SysUser::getCreateTime, todayStart)));
        vo.setActiveTodayUsers(userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().ge(SysUser::getLoginTime, todayStart)));
        vo.setDisabledUsers(userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 0)));

        // 商品统计
        vo.setProductTotal(productMapper.selectCount(null));
        vo.setOnSaleProducts(productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));

        // 订单统计
        vo.setOrderTotal(orderMapper.selectCount(null));
        vo.setPaidOrders(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, PAID_STATUSES)));
        vo.setPendingShipOrders(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 1)));

        // 销售总额：已支付订单实付金额合计
        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, PAID_STATUSES)
                        .select(Order::getPayAmount));
        BigDecimal totalSales = paidOrders.stream()
                .map(Order::getPayAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalSales(totalSales);

        log.info("[AdminStatistics] 数据概览统计完成: userTotal={}, orderTotal={}, totalSales={}",
                vo.getUserTotal(), vo.getOrderTotal(), totalSales);
        return vo;
    }
}
