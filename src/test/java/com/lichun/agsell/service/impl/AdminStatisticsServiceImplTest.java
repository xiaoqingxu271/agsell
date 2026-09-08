package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.AdminStatisticsVO;
import com.lichun.agsell.model.vo.StatisticsTrendVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    /** 纯 Mockito 环境缺少 MyBatis-Plus TableInfo 缓存，先初始化 Lambda 列映射 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Order.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Product.class);
    }

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

    @Test
    @DisplayName("趋势统计：日期序列完整且缺失日期补零")
    void trendBuildsCompleteDateSeries() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6L);

        // 第 1 天有 5 个新用户、2 笔订单、销售额 49.90；今天有 3 个新用户；中间日期无数据
        List<Map<String, Object>> userRows = List.of(
                Map.of("day", java.sql.Date.valueOf(start), "cnt", 5L),
                Map.of("day", java.sql.Date.valueOf(today), "cnt", 3L));
        List<Map<String, Object>> orderCountRows = List.of(
                Map.of("day", java.sql.Date.valueOf(start), "cnt", 2L));
        List<Map<String, Object>> salesRows = List.of(
                Map.of("day", java.sql.Date.valueOf(start), "total", new BigDecimal("49.90")));

        when(userMapper.selectMaps(any(Wrapper.class))).thenReturn(userRows);
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(orderCountRows, salesRows);

        StatisticsTrendVO vo = statisticsService.getTrend(7);

        assertEquals(7, vo.getDates().size());
        assertEquals(start.toString(), vo.getDates().get(0));
        assertEquals(today.toString(), vo.getDates().get(6));
        assertEquals(5L, vo.getNewUsers().get(0));
        assertEquals(3L, vo.getNewUsers().get(6));
        assertEquals(0L, vo.getNewUsers().get(1)); // 中间无数据补零
        assertEquals(2L, vo.getOrderCounts().get(0));
        assertEquals(0L, vo.getOrderCounts().get(6));
        assertEquals(0, new BigDecimal("49.90").compareTo(vo.getSales().get(0)));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getSales().get(6))); // 今天无销售补零
    }

    @Test
    @DisplayName("趋势统计：days 越界收敛到 1~30")
    void trendClampsDays() {
        LocalDate today = LocalDate.now();
        when(userMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(), List.of());

        StatisticsTrendVO vo30 = statisticsService.getTrend(999);
        assertEquals(30, vo30.getDates().size());
        assertEquals(today.toString(), vo30.getDates().get(29));

        StatisticsTrendVO vo1 = statisticsService.getTrend(0);
        assertEquals(1, vo1.getDates().size());
        assertEquals(today.toString(), vo1.getDates().get(0));
    }
}
