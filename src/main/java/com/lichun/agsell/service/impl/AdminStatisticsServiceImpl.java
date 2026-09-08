package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.AdminStatisticsVO;
import com.lichun.agsell.model.vo.StatisticsTrendVO;
import com.lichun.agsell.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public StatisticsTrendVO getTrend(int days) {
        int n = Math.min(Math.max(days, 1), 30);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(n - 1L);

        // 近 N 天完整日期序列（升序）
        List<LocalDate> dateList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            dateList.add(startDate.plusDays(i));
        }

        // 每日新增用户（按 createTime）
        Map<LocalDate, Long> newUsersByDay = new HashMap<>();
        List<Map<String, Object>> userRows = userMapper.selectMaps(new QueryWrapper<SysUser>()
                .select("DATE(create_time) AS day", "COUNT(*) AS cnt")
                .ge("create_time", startDate.atStartOfDay())
                .groupBy("DATE(create_time)"));
        for (Map<String, Object> row : userRows) {
            newUsersByDay.put(toLocalDate(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        // 每日订单数（按 createTime）
        Map<LocalDate, Long> orderCountsByDay = new HashMap<>();
        List<Map<String, Object>> orderRows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("DATE(create_time) AS day", "COUNT(*) AS cnt")
                .ge("create_time", startDate.atStartOfDay())
                .groupBy("DATE(create_time)"));
        for (Map<String, Object> row : orderRows) {
            orderCountsByDay.put(toLocalDate(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        // 每日销售额（已支付订单，按 payTime）
        Map<LocalDate, BigDecimal> salesByDay = new HashMap<>();
        List<Map<String, Object>> salesRows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("DATE(pay_time) AS day", "COALESCE(SUM(pay_amount), 0) AS total")
                .in("status", PAID_STATUSES)
                .ge("pay_time", startDate.atStartOfDay())
                .groupBy("DATE(pay_time)"));
        for (Map<String, Object> row : salesRows) {
            salesByDay.put(toLocalDate(row.get("day")), new BigDecimal(row.get("total").toString()));
        }

        // 组装并补零（无数据日期填 0）
        StatisticsTrendVO vo = new StatisticsTrendVO();
        List<String> dateStrs = new ArrayList<>();
        List<Long> newUserList = new ArrayList<>();
        List<Long> orderCountList = new ArrayList<>();
        List<BigDecimal> salesList = new ArrayList<>();
        for (LocalDate d : dateList) {
            dateStrs.add(d.toString());
            newUserList.add(newUsersByDay.getOrDefault(d, 0L));
            orderCountList.add(orderCountsByDay.getOrDefault(d, 0L));
            salesList.add(salesByDay.getOrDefault(d, BigDecimal.ZERO));
        }
        vo.setDates(dateStrs);
        vo.setNewUsers(newUserList);
        vo.setOrderCounts(orderCountList);
        vo.setSales(salesList);

        log.info("[AdminStatistics] 数据趋势统计完成: days={}, startDate={}, endDate={}",
                n, dateList.get(0), dateList.get(n - 1));
        return vo;
    }

    /** JDBC 返回的日期列（DATE 类型）统一转 LocalDate */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
