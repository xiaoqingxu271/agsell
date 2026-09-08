package com.lichun.agsell.service;

import com.lichun.agsell.model.vo.AdminStatisticsVO;
import com.lichun.agsell.model.vo.StatisticsTrendVO;

/**
 * 管理端数据统计服务
 */
public interface AdminStatisticsService {

    /**
     * 获取数据概览统计（用户/商品/订单核心指标）
     */
    AdminStatisticsVO getOverview();

    /**
     * 获取数据趋势统计（近 N 天新增用户/订单数/销售额）
     *
     * @param days 统计天数，1~30，非法值自动收敛
     */
    StatisticsTrendVO getTrend(int days);
}
