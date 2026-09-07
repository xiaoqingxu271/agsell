package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.vo.AdminStatisticsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 链路 B 修复验证：数据概览统计口径
 */
@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private AdminStatisticsServiceImpl statisticsService;

    @Test
    @DisplayName("统计指标与销售总额口径正确")
    void overviewAggregatesCorrectly() {
        // 用户：总数100，今日新增3、今日活跃20、禁用2（按调用顺序匹配）
        when(userMapper.selectCount(null)).thenReturn(100L);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(3L, 20L, 2L);

        // 商品：总数50，在售30
        when(productMapper.selectCount(null)).thenReturn(50L);
        when(productMapper.selectCount(any(Wrapper.class))).thenReturn(30L);

        // 订单：总数200，已支付150、待发货10
        when(orderMapper.selectCount(null)).thenReturn(200L);
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(150L, 10L);

        // 销售总额：已支付订单 payAmount 合计 19.90 + 30.00 = 49.90
        Order paid1 = new Order();
        paid1.setPayAmount(new BigDecimal("19.90"));
        Order paid2 = new Order();
        paid2.setPayAmount(new BigDecimal("30.00"));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(paid1, paid2));

        AdminStatisticsVO vo = statisticsService.getOverview();

        assertEquals(100L, vo.getUserTotal());
        assertEquals(3L, vo.getTodayNewUsers());
        assertEquals(20L, vo.getActiveTodayUsers());
        assertEquals(2L, vo.getDisabledUsers());
        assertEquals(50L, vo.getProductTotal());
        assertEquals(30L, vo.getOnSaleProducts());
        assertEquals(200L, vo.getOrderTotal());
        assertEquals(150L, vo.getPaidOrders());
        assertEquals(10L, vo.getPendingShipOrders());
        assertEquals(0, new BigDecimal("49.90").compareTo(vo.getTotalSales()));
    }

    @Test
    @DisplayName("无已支付订单时销售总额为0")
    void overviewWithNoPaidOrders() {
        when(userMapper.selectCount(null)).thenReturn(0L);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 0L, 0L);
        when(productMapper.selectCount(null)).thenReturn(0L);
        when(productMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(orderMapper.selectCount(null)).thenReturn(0L);
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 0L);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        AdminStatisticsVO vo = statisticsService.getOverview();

        assertEquals(0L, vo.getOrderTotal());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalSales()));
    }
}
