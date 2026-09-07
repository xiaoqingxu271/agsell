package com.lichun.agsell.task;

import com.lichun.agsell.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动取消定时任务
 * 每分钟扫描一次，将超过 order.timeout-minutes 分钟仍未支付的待付款订单自动置为已取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelExpiredOrders() {
        try {
            int count = orderService.cancelExpiredOrders(timeoutMinutes);
            if (count > 0) {
                log.info("订单超时自动取消完成，共取消 {} 笔待付款订单（超时阈值 {} 分钟）", count, timeoutMinutes);
            }
        } catch (Exception e) {
            log.error("订单超时自动取消任务执行失败", e);
        }
    }
}
