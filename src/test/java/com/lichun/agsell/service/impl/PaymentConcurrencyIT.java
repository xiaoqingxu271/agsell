package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发重复支付集成测试（真实 MySQL）：N 线程同时支付同一待付款订单。
 * 预期：恰好 1 个事务成功，其余被"支付权原子抢占"拒绝；库存只扣减一次。
 * 数据自建自清，不依赖种子数据。
 */
@SpringBootTest
class PaymentConcurrencyIT {

    private static final long PRODUCT_ID = 2097470000000000001L;
    private static final long ORDER_ID = 2097470000000000101L;
    private static final long USER_ID = 2097470000000000901L;
    private static final int THREADS = 16;
    private static final int QUANTITY = 2;
    private static final int INITIAL_STOCK = 100;

    @Autowired
    private PaymentServiceImpl paymentService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProductMapper productMapper;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM `order` WHERE id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }

    @Test
    @DisplayName("16 线程并发支付同一订单：恰好 1 笔成功 + 库存只扣一次")
    void concurrentDoublePay_onlyOneWins() throws Exception {
        // 1. 造数据：用户 + 无规格商品（库存100）+ 待付款订单（买2件）
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, nickname, status) VALUES (?, ?, 'pwd', '支付并发测试', 1)",
                USER_ID, "pay_conc_test");
        jdbcTemplate.update(
                "INSERT INTO product (id, name, category_id, price, stock, status, deleted) VALUES (?, '支付并发测试商品', 1, 9.90, ?, 1, 0)",
                PRODUCT_ID, INITIAL_STOCK);
        jdbcTemplate.update(
                "INSERT INTO `order` (id, order_no, user_id, address_id, receiver, phone, address, total_amount, freight, discount, pay_amount, status, deleted) " +
                        "VALUES (?, 'AGSPAYCONCTEST0001', ?, 0, '测试', '13800000000', '测试地址', 19.80, 0, 0, 19.80, 0, 0)",
                ORDER_ID, USER_ID);
        jdbcTemplate.update(
                "INSERT INTO order_item (id, order_id, product_id, product_name, price, quantity, subtotal) VALUES (?, ?, ?, '支付并发测试商品', 9.90, ?, 19.80)",
                ORDER_ID + 1, ORDER_ID, PRODUCT_ID, QUANTITY);

        // 2. 并发支付
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger other = new AtomicInteger(0);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    BaseContext.setCurrentId(USER_ID, "pay-conc-it");
                    try {
                        paymentService.createPayment("AGSPAYCONCTEST0001", 1);
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        other.incrementAndGet();
                    } finally {
                        BaseContext.removeCurrentId();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发支付 60 秒内未完成");
        pool.shutdown();

        // 3. 校验：恰好一笔成功；库存恰好扣一次；订单状态已支付
        assertEquals(1, success.get(), "并发重复支付必须恰好成功一笔");
        assertEquals(THREADS - 1, rejected.get(), "其余应全部被支付权抢占拒绝");
        assertEquals(0, other.get(), "不应出现系统级异常");
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, PRODUCT_ID);
        assertEquals(INITIAL_STOCK - QUANTITY, stock, "库存必须恰好扣减一次");
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM `order` WHERE id = ?", Integer.class, ORDER_ID);
        assertEquals(1, status, "订单应处于待发货（已支付）状态");
        assertTrue(productMapper.selectById(PRODUCT_ID) != null);
    }
}
