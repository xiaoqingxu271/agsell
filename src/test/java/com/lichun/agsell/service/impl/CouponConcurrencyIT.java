package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.dto.CouponCreateRequest;
import com.lichun.agsell.model.vo.UserCouponVO;
import com.lichun.agsell.service.CouponRedisService;
import com.lichun.agsell.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优惠券核心链路高并发集成测试（真实 MySQL + Redis）
 * 场景：100 个不同用户并发领取 1 张总量为 50 的限量券。
 * 预期：恰好 50 张领取成功（防超发），另 50 张返回"已领完"，全程无重复领取。
 * 注意：该测试依赖本地 local 环境（MySQL/Redis），不随全量单测运行（文件名 IT 后缀）。
 */
@SpringBootTest
class CouponConcurrencyIT {

    private static final int USER_COUNT = 100;
    private static final int STOCK = 50;

    @Autowired
    private CouponService couponService;
    @Autowired
    private CouponRedisService couponRedisService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("100 并发领 50 张券：恰好 50 张成功 + 50 张售罄 + 无重复 + DB 计数一致，并输出耗时")
    void concurrent_100users_vs_50coupons() throws Exception {
        // 1. 清理历史测试数据（幂等）
        cleanupTestData();

        // 2. 创建限量券模板（总量 50）
        CouponCreateRequest request = new CouponCreateRequest();
        request.setCouponName("压测限量券");
        request.setCouponType(1);
        request.setThreshold(new BigDecimal("0.00"));
        request.setAmount(new BigDecimal("5.00"));
        request.setTotalCount(STOCK);
        request.setPerUserLimit(1);
        request.setValidDays(7);
        request.setSort(999);
        request.setStatus(1);
        Long couponId = couponService.createCoupon(request);

        try {
            // 3. 批量造 100 个测试用户
            List<Long> userIds = createTestUsers(USER_COUNT);

            // 4. 100 线程并发领取
            ExecutorService pool = Executors.newFixedThreadPool(USER_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(USER_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);
            AtomicInteger repeatCount = new AtomicInteger(0);
            AtomicInteger otherErrorCount = new AtomicInteger(0);
            AtomicLong totalElapsedMs = new AtomicLong(0);
            List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            long wallStart = System.currentTimeMillis();
            for (Long uid : userIds) {
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        long t0 = System.nanoTime();
                        BaseContext.setCurrentId(uid, "coupon-it");
                        try {
                            UserCouponVO vo = couponService.receiveCoupon(couponId);
                            successCount.incrementAndGet();
                            if (vo.getStatus() != 0) {
                                errors.add("状态异常: " + vo.getStatus());
                            }
                        } catch (BusinessException ex) {
                            if (ex.getCode() == ErrorCode.COUPON_SOLD_OUT.getCode()) {
                                soldOutCount.incrementAndGet();
                            } else if (ex.getCode() == ErrorCode.COUPON_RECEIVE_REPEAT.getCode()) {
                                repeatCount.incrementAndGet();
                            } else {
                                otherErrorCount.incrementAndGet();
                                errors.add("业务异常:" + ex.getCode() + " " + ex.getMessage());
                            }
                        } catch (Exception ex) {
                            otherErrorCount.incrementAndGet();
                            errors.add("系统异常:" + ex.getMessage());
                        } finally {
                            totalElapsedMs.addAndGet((System.nanoTime() - t0) / 1_000_000);
                            BaseContext.removeCurrentId();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown(); // 同时放行
            boolean finished = doneLatch.await(120, TimeUnit.SECONDS);
            long wallElapsed = System.currentTimeMillis() - wallStart;
            pool.shutdown();

            // 5. 校验（防超发 + 防重复）
            assertTrue(finished, "压测 120 秒内未完成");
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_coupon WHERE coupon_id = ?", Integer.class, couponId);
            Integer receivedCount = jdbcTemplate.queryForObject(
                    "SELECT received_count FROM coupon WHERE id = ?", Integer.class, couponId);
            Integer distinctUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM user_coupon WHERE coupon_id = ?", Integer.class, couponId);

            assertEquals(STOCK, successCount.get(), "成功领取数应恰好等于券总量");
            assertEquals(STOCK, soldOutCount.get(), "其余请求应全部售罄");
            assertEquals(0, repeatCount.get(), "不同用户不应出现重复标记");
            assertEquals(0, otherErrorCount.get(), "不应出现系统异常");
            assertEquals(STOCK, dbCount, "DB 领取记录数应为券总量");
            assertEquals(STOCK, receivedCount, "模板冗余计数应为券总量");
            assertEquals(STOCK, distinctUsers, "一人一张：领取用户数应等于券总量");
            assertTrue(errors.isEmpty(), "校验异常: " + errors);

            // 6. 输出耗时
            double wallSeconds = wallElapsed / 1000.0;
            double avgLatency = (double) totalElapsedMs.get() / USER_COUNT;
            System.out.println("============================================================");
            System.out.println("[CouponConcurrencyIT] 100 并发领 50 张券压测结果");
            System.out.println("  总请求数: " + USER_COUNT);
            System.out.println("  成功领取: " + successCount.get() + "（防超发校验通过）");
            System.out.println("  售罄拒绝: " + soldOutCount.get());
            System.out.println("  墙钟总耗时: " + wallElapsed + " ms");
            System.out.println("  接口 QPS（100 请求/总耗时）: " + String.format("%.2f", USER_COUNT / wallSeconds));
            System.out.println("  平均单请求处理耗时: " + String.format("%.1f", avgLatency) + " ms");
            System.out.println("  备注: 与秒杀压测同口径（100 并发下的实测吞吐）");
            System.out.println("============================================================");
        } finally {
            // 7. 清理测试数据与缓存
            cleanupTestData();
            couponRedisService.deleteCache(couponId);
        }
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM user_coupon WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'coupon_test_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'coupon_test_%'");
        jdbcTemplate.update("DELETE FROM coupon WHERE coupon_name = '压测限量券'");
    }

    private List<Long> createTestUsers(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, 1)",
                    "coupon_test_" + i, "pwd", "领券压测用户" + i);
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE username = ?", Long.class, "coupon_test_" + i);
            ids.add(id);
        }
        return ids;
    }
}
