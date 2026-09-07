package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 管理端数据概览统计
 */
@Data
public class AdminStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户总数 */
    private Long userTotal;

    /** 今日新增用户 */
    private Long todayNewUsers;

    /** 今日活跃用户（今日有登录记录） */
    private Long activeTodayUsers;

    /** 禁用用户数 */
    private Long disabledUsers;

    /** 商品总数 */
    private Long productTotal;

    /** 在售商品数 */
    private Long onSaleProducts;

    /** 订单总数 */
    private Long orderTotal;

    /** 已支付订单数（含待发货/待收货/已完成/售后中） */
    private Long paidOrders;

    /** 待发货订单数 */
    private Long pendingShipOrders;

    /** 销售总额（已支付订单实付金额合计） */
    private BigDecimal totalSales;
}
