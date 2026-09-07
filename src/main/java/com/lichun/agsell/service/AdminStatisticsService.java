package com.lichun.agsell.service;

import com.lichun.agsell.model.vo.AdminStatisticsVO;

/**
 * 管理端数据统计服务
 */
public interface AdminStatisticsService {

    /**
     * 获取数据概览统计（用户/商品/订单核心指标）
     */
    AdminStatisticsVO getOverview();
}
