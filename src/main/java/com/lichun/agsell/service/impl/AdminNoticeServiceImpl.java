package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.model.vo.NoticeSummaryVO;
import com.lichun.agsell.service.AdminNoticeService;
import com.lichun.agsell.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 管理端通知中心：铃铛待办汇总
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeServiceImpl implements AdminNoticeService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final SysConfigService sysConfigService;

    /** 售后待处理状态码（与 AdminAfterSalesServiceImpl 中 STATUS_PENDING 一致） */
    private static final int AFTER_SALES_PENDING = 0;

    @Override
    public NoticeSummaryVO summary() {
        NoticeSummaryVO vo = new NoticeSummaryVO();

        // 1. 待发货订单（已付款待发货）
        long pendingShip = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatusEnum.PENDING_SHIPMENT.getCode())
                .eq(Order::getDeleted, 0));
        vo.setPendingShipCount(pendingShip);

        // 2. 低库存商品（库存低于预警阈值）
        int threshold = parseIntSafely(
                sysConfigService.getConfigOrDefault("low_stock_threshold", "10"), 10);
        long lowStock = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .lt(Product::getStock, threshold)
                .eq(Product::getDeleted, 0));
        vo.setLowStockCount(lowStock);

        // 3. 待处理售后
        long pendingAfterSales = afterSalesMapper.selectCount(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getStatus, AFTER_SALES_PENDING)
                .eq(AfterSales::getDeleted, 0));
        vo.setPendingAfterSalesCount(pendingAfterSales);

        vo.setTotal(pendingShip + lowStock + pendingAfterSales);
        return vo;
    }

    private int parseIntSafely(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
