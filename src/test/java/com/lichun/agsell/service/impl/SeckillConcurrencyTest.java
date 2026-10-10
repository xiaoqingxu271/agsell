package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.dto.SeckillActivityRequest;
import com.lichun.agsell.model.dto.SeckillOrderRequest;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.service.RedisTokenService;
import com.lichun.agsell.service.SeckillRedisService;
import com.lichun.agsell.service.SeckillService;
import com.lichun.agsell.service.SysConfigService;
import com.lichun.agsell.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 秒杀核心链路高并发集成测试（真实 MySQL + Redis）
 * 场景：100 个不同用户并发抢购 1 个库存为 50 的秒杀活动。
 * 预期：恰好 50 笔成功订单（防超卖），另 50 笔返回"已抢光"，全程无重复用户下单。
 * 注意：该测试依赖本地 local 环境（MySQL/Redis），不随全量单测运行（surefire 默认不识别 IT 后缀，已更名 *Test 纳入 mvnw verify）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillConcurrencyTest {

    private static final int USER_COUNT = 100;
    private static final int STOCK = 50;
    private static final BigDecimal SECKILL_PRICE = new BigDecimal("3.90");

    @LocalServerPort
    private int port;

    @Autowired
    private SeckillService seckillService;
    @Autowired
    private SeckillRedisService seckillRedisService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private RedisTokenService redisTokenService;
    @Autowired
    private SysConfigService sysConfigService;

    @Test
    @DisplayName("100 并发抢 50 库存：恰好 50 单成功 + 50 单售罄 + 一人一单 + 金额=秒杀价+运费，并输出 QPS")
    void concurrent_100users_vs_50stock() throws Exception {
        // 1. 清理历史测试数据（幂等）并自建压测商品
        cleanupTestData();
        long productId = seedProduct("seckill_it_库存商品", 500, "12.90");

        // 2. 创建秒杀活动（进行中）
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(productId);
        request.setSeckillPrice(SECKILL_PRICE);
        request.setSeckillStock(STOCK);
        request.setSeckillLimit(1);
        request.setStartTime(LocalDateTime.now().minusMinutes(1));
        request.setEndTime(LocalDateTime.now().plusHours(1));
        request.setSort(0);
        request.setStatus(1);
        Long activityId = seckillService.createActivity(request);
        String activityCode = queryActivityCode(activityId);

        try {
            // 3. 批量造 100 个测试用户 + 每人一条默认地址
            List<Long> userIds = createTestUsers(USER_COUNT);
            java.util.Map<Long, Long> addressIdMap = createTestAddresses(userIds);

            // 4. 100 线程并发抢购
            ExecutorService pool = Executors.newFixedThreadPool(USER_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(USER_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);
            AtomicInteger repeatCount = new AtomicInteger(0);
            AtomicInteger otherErrorCount = new AtomicInteger(0);
            AtomicLong totalElapsedMs = new AtomicLong(0);
            List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            long begin = System.currentTimeMillis();
            for (int i = 0; i < USER_COUNT; i++) {
                final Long uid = userIds.get(i);
                final Long addressId = addressIdMap.get(uid);
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        long t0 = System.nanoTime();
                        BaseContext.setCurrentId(uid, "seckill-it");
                        try {
                            SeckillOrderRequest orderRequest = new SeckillOrderRequest();
                            orderRequest.setActivityCode(activityCode);
                            orderRequest.setAddressId(addressId);
                            OrderCreateVO vo = seckillService.createSeckillOrder(orderRequest);
                            successCount.incrementAndGet();
                            // 金额校验：秒杀价 + 运费（与主服务 computeFreight 同口径）
                            BigDecimal expected = SECKILL_PRICE.add(expectedFreight(SECKILL_PRICE));
                            if (vo.getPayAmount().compareTo(expected) != 0) {
                                errors.add("金额异常: " + vo.getPayAmount() + " 期望: " + expected);
                            }
                        } catch (BusinessException ex) {
                            if (ex.getCode() == ErrorCode.SECKILL_SOLD_OUT.getCode()) {
                                soldOutCount.incrementAndGet();
                            } else if (ex.getCode() == ErrorCode.SECKILL_REPEAT.getCode()) {
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
            long wallStart = System.currentTimeMillis();
            startLatch.countDown(); // 同时放行
            boolean finished = doneLatch.await(120, TimeUnit.SECONDS);
            long wallElapsed = System.currentTimeMillis() - wallStart;
            pool.shutdown();

            // 5. 校验
            assertTrue(finished, "压测 120 秒内未完成");
            Integer remaining = seckillRedisService.getRemainingStock(activityId);
            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `order` WHERE seckill_activity_id = ?",
                    Integer.class, activityId);
            Integer userCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM `order` WHERE seckill_activity_id = ?",
                    Integer.class, activityId);

            // 防超卖：成功单数 == 库存
            assertEquals(STOCK, successCount.get(), "成功订单数应恰好等于秒杀库存");
            assertEquals(STOCK, soldOutCount.get(), "其余请求应全部售罄");
            assertEquals(0, repeatCount.get(), "不同用户不应出现重复标记");
            assertEquals(0, otherErrorCount.get(), "不应出现系统异常");
            assertEquals(0, remaining, "Redis 剩余库存应为 0");
            assertEquals(STOCK, orderCount, "DB 秒杀订单数应为库存数");
            assertEquals(STOCK, userCount, "一人一单：成功订单用户数应等于库存数");
            assertTrue(errors.isEmpty(), "校验异常: " + errors);

            // 6. 输出 QPS（接口吞吐 = 总请求数 / 墙钟总耗时）
            double wallSeconds = wallElapsed / 1000.0;
            double avgLatency = (double) totalElapsedMs.get() / USER_COUNT;
            System.out.println("============================================================");
            System.out.println("[SeckillConcurrencyTest] 100 并发抢 50 库存压测结果");
            System.out.println("  总请求数: " + USER_COUNT);
            System.out.println("  成功订单: " + successCount.get() + "（防超卖校验通过）");
            System.out.println("  售罄拒绝: " + soldOutCount.get());
            System.out.println("  墙钟总耗时: " + wallElapsed + " ms");
            System.out.println("  接口 QPS（100 请求/总耗时）: " + String.format("%.2f", USER_COUNT / wallSeconds));
            System.out.println("  平均单请求处理耗时: " + String.format("%.1f", avgLatency) + " ms");
            System.out.println("  备注: 本机单实例 + 内嵌 Tomcat 默认线程池，QPS 为 100 并发下的实测吞吐");
            System.out.println("============================================================");
        } finally {
            // 清理测试活动与订单数据
            cleanupTestData();
            seckillRedisService.deleteSnapshot(activityId);
        }
    }

    @Test
    // 放大档压测耗时较长，默认跳过；手动全量压测：./mvnw verify -Dstress.test=true
    @EnabledIfSystemProperty(named = "stress.test", matches = "true")
    @DisplayName("500 并发抢 180 库存：放大档压测，验证高并发下防超卖与吞吐衰减")
    void concurrent_500users_vs_200stock() throws Exception {
        final int users = 500;
        final int stock = 180; // 秒杀库存须不超过自建商品库存
        cleanupTestData();
        long productId = seedProduct("seckill_it_库存商品", 500, "12.90");

        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(productId);
        request.setSeckillPrice(SECKILL_PRICE);
        request.setSeckillStock(stock);
        request.setSeckillLimit(1);
        request.setStartTime(LocalDateTime.now().minusMinutes(1));
        request.setEndTime(LocalDateTime.now().plusHours(1));
        request.setSort(0);
        request.setStatus(1);
        Long activityId = seckillService.createActivity(request);
        String activityCode = queryActivityCode(activityId);

        try {
            List<Long> userIds = createTestUsers(users);
            java.util.Map<Long, Long> addressIdMap = createTestAddresses(userIds);

            ExecutorService pool = Executors.newFixedThreadPool(users);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(users);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);
            AtomicInteger repeatCount = new AtomicInteger(0);
            AtomicInteger otherErrorCount = new AtomicInteger(0);
            List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());
            List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < users; i++) {
                final Long uid = userIds.get(i);
                final Long addressId = addressIdMap.get(uid);
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        long t0 = System.nanoTime();
                        BaseContext.setCurrentId(uid, "seckill-it-500");
                        try {
                            SeckillOrderRequest orderRequest = new SeckillOrderRequest();
                            orderRequest.setActivityCode(activityCode);
                            orderRequest.setAddressId(addressId);
                            seckillService.createSeckillOrder(orderRequest);
                            successCount.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.getCode() == ErrorCode.SECKILL_SOLD_OUT.getCode()) {
                                soldOutCount.incrementAndGet();
                            } else if (ex.getCode() == ErrorCode.SECKILL_REPEAT.getCode()) {
                                repeatCount.incrementAndGet();
                            } else {
                                otherErrorCount.incrementAndGet();
                                errors.add("业务异常:" + ex.getCode() + " " + ex.getMessage());
                            }
                        } catch (Exception ex) {
                            otherErrorCount.incrementAndGet();
                            errors.add("系统异常:" + ex.getMessage());
                        } finally {
                            latencies.add((System.nanoTime() - t0) / 1_000_000);
                            BaseContext.removeCurrentId();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            long wallStart = System.currentTimeMillis();
            startLatch.countDown();
            boolean finished = doneLatch.await(300, TimeUnit.SECONDS);
            long wallElapsed = System.currentTimeMillis() - wallStart;
            pool.shutdown();

            assertTrue(finished, "500 并发压测 300 秒内未完成");
            Integer remaining = seckillRedisService.getRemainingStock(activityId);
            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `order` WHERE seckill_activity_id = ?",
                    Integer.class, activityId);
            Integer distinctUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM `order` WHERE seckill_activity_id = ?",
                    Integer.class, activityId);

            assertEquals(stock, successCount.get(), "成功订单数应恰好等于秒杀库存");
            assertEquals(users - stock, soldOutCount.get(), "其余请求应全部售罄");
            assertEquals(0, repeatCount.get(), "不同用户不应出现重复标记");
            assertEquals(0, otherErrorCount.get(), "不应出现系统异常");
            assertEquals(0, remaining, "Redis 剩余库存应为 0");
            assertEquals(stock, orderCount, "DB 秒杀订单数应为库存数");
            assertEquals(stock, distinctUsers, "一人一单：成功订单用户数应等于库存数");
            assertTrue(errors.isEmpty(), "校验异常: " + errors.stream().limit(5).toList());

            List<Long> sorted = latencies.stream().sorted().toList();
            double wallSeconds = wallElapsed / 1000.0;
            long p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
            System.out.println("============================================================");
            System.out.println("[SeckillConcurrencyTest] 500 并发抢 180 库存压测结果");
            System.out.println("  总请求数: " + users);
            System.out.println("  成功订单: " + successCount.get() + "（防超卖校验通过）");
            System.out.println("  售罄拒绝: " + soldOutCount.get());
            System.out.println("  墙钟总耗时: " + wallElapsed + " ms");
            System.out.println("  接口 QPS: " + String.format("%.2f", users / wallSeconds));
            System.out.println("  平均单请求耗时: " + String.format("%.1f", sorted.stream().mapToLong(Long::longValue).average().orElse(0)) + " ms");
            System.out.println("  P95 单请求耗时: " + p95 + " ms");
            System.out.println("============================================================");
        } finally {
            cleanupTestData();
            seckillRedisService.deleteSnapshot(activityId);
        }
    }

    @Test
    @DisplayName("HTTP 层 100 并发抢 50 库存：真实接口吞吐与 QPS")
    void concurrent_http_100users_vs_50stock() throws Exception {
        // 1. 清理 + 自建商品 + 造活动（秒杀价 18.80）
        cleanupTestData();
        long productId = seedProduct("seckill_it_http商品", 500, "68.00");
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(productId);
        request.setSeckillPrice(new BigDecimal("18.80"));
        request.setSeckillStock(STOCK);
        request.setSeckillLimit(1);
        request.setStartTime(LocalDateTime.now().minusMinutes(1));
        request.setEndTime(LocalDateTime.now().plusHours(1));
        request.setStatus(1);
        Long activityId = seckillService.createActivity(request);
        String activityCode = queryActivityCode(activityId);

        // 2. 造 100 用户 + 地址 + 签发 100 个 JWT（写入 Redis 白名单模拟真实登录）
        List<Long> userIds = createTestUsers(USER_COUNT);
        java.util.Map<Long, Long> addressIdMap = createTestAddresses(userIds);
        List<String> tokens = new ArrayList<>(USER_COUNT);
        for (int i = 0; i < USER_COUNT; i++) {
            Long uid = userIds.get(i);
            String token = jwtUtils.generateToken(uid);
            String jti = jwtUtils.parseToken(token).get("jti", String.class);
            redisTokenService.saveUserToken(uid, token, jti, 3600);
            tokens.add(token);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(USER_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < USER_COUNT; i++) {
            final String token = tokens.get(i);
            final Long addressId = addressIdMap.get(userIds.get(i));
            pool.submit(() -> {
                try {
                    startLatch.await();
                    String body = "{\"activityCode\":\"" + activityCode + "\",\"addressId\":" + addressId + ",\"remark\":\"\"}";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/seckill/order"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    String respBody = resp.body();
                    if (respBody.contains("\"code\":0")) {
                        successCount.incrementAndGet();
                    } else if (respBody.contains("50012") || respBody.contains("SECKILL_SOLD_OUT")) {
                        soldOutCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finished = doneLatch.await(120, TimeUnit.SECONDS);
        long wallElapsed = System.currentTimeMillis() - wallStart;
        pool.shutdown();

        // 3. 校验
        assertTrue(finished, "HTTP 压测 120 秒内未完成");
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE seckill_activity_id = ?", Integer.class, activityId);
        assertEquals(STOCK, successCount.get(), "HTTP 层成功订单数应恰好等于库存");
        assertEquals(0, otherErrorCount.get(), "HTTP 层不应出现系统错误");
        assertEquals(STOCK, orderCount, "DB 秒杀订单数应为库存数");

        double wallSeconds = wallElapsed / 1000.0;
        System.out.println("============================================================");
        System.out.println("[SeckillConcurrencyTest] HTTP 接口层 100 并发抢 50 库存压测结果");
        System.out.println("  总请求数: " + USER_COUNT);
        System.out.println("  成功订单: " + successCount.get());
        System.out.println("  售罄拒绝: " + soldOutCount.get());
        System.out.println("  墙钟总耗时: " + wallElapsed + " ms");
        System.out.println("  接口 QPS（100 请求/总耗时）: " + String.format("%.2f", USER_COUNT / wallSeconds));
        System.out.println("============================================================");

        // 4. 清理
        cleanupTestData();
        seckillRedisService.deleteSnapshot(activityId);
    }

    private String queryActivityCode(Long activityId) {
        return jdbcTemplate.queryForObject(
                "SELECT activity_code FROM seckill_activity WHERE id = ?", String.class, activityId);
    }

    /** 与 SeckillServiceImpl#computeFreight 同口径：金额 ≥ 包邮阈值（阈值>0）免运费，否则默认运费 */
    private BigDecimal expectedFreight(BigDecimal amount) {
        BigDecimal threshold = new BigDecimal(sysConfigService.getConfigOrDefault("free_shipping_threshold", "0"));
        BigDecimal defaultFreight = new BigDecimal(sysConfigService.getConfigOrDefault("default_freight", "0"));
        if (threshold.signum() > 0 && amount.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return defaultFreight;
    }

    /** 自建压测商品（全新空库可运行）；并发测试未启用 @Transactional，靠 cleanupTestData 清理 */
    private long seedProduct(String name, int stock, String price) {
        jdbcTemplate.update(
                "INSERT INTO product_category (name, sort, status) VALUES ('秒杀集成测试分类', 999, 1)");
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_category WHERE name = '秒杀集成测试分类' LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO product (name, category_id, price, stock, sales, status, sort) VALUES (?, ?, ?, ?, 0, 1, 999)",
                name, categoryId, new BigDecimal(price), stock);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ? LIMIT 1", Long.class, name);
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE oi FROM order_item oi JOIN `order` o ON oi.order_id = o.id WHERE o.user_id IN (SELECT id FROM sys_user WHERE username LIKE 'seckill_test_%')");
        jdbcTemplate.update("DELETE FROM `order` WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'seckill_test_%')");
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'seckill_test_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'seckill_test_%'");
        jdbcTemplate.update("DELETE FROM seckill_activity WHERE product_id IN (SELECT id FROM (SELECT id FROM product WHERE name LIKE 'seckill_it_%') t)");
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'seckill_it_%'");
        jdbcTemplate.update("DELETE FROM product_category WHERE name = '秒杀集成测试分类'");
    }

    private List<Long> createTestUsers(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, 1)",
                    "seckill_test_" + i, "pwd", "压测用户" + i);
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE username = ?", Long.class, "seckill_test_" + i);
            ids.add(id);
        }
        return ids;
    }

    private java.util.Map<Long, Long> createTestAddresses(List<Long> userIds) {
        java.util.Map<Long, Long> map = new java.util.HashMap<>();
        for (Long uid : userIds) {
            jdbcTemplate.update(
                    "INSERT INTO user_address (user_id, receiver, phone, province, city, district, detail, is_default) VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                    uid, "测试收货人", "13800000000", "江西省", "赣州市", "章贡区", "压测地址" + uid);
            Long addressId = jdbcTemplate.queryForObject(
                    "SELECT id FROM user_address WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, uid);
            map.put(uid, addressId);
        }
        return map;
    }
}

