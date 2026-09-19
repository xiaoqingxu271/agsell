package com.lichun.agsell.task;

import com.lichun.agsell.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 优惠券过期扫描定时任务
 * 每日 03:00 将「未使用且已过期」的用户券批量置为已过期（仅状态收敛；核销/展示仍实时校验 expire_time）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireScheduler {

    private final CouponService couponService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void expireCoupons() {
        try {
            int count = couponService.expireCoupons();
            if (count > 0) {
                log.info("优惠券过期批处理完成，共置为过期 {} 张", count);
            }
        } catch (Exception e) {
            log.error("优惠券过期批处理任务执行失败", e);
        }
    }
}
