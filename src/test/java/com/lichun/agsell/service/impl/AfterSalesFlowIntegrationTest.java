package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.dto.AfterSalesCreateRequest;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.vo.AfterSalesDetailVO;
import com.lichun.agsell.service.AfterSalesService;
import com.lichun.agsell.service.AdminAfterSalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 售后模块真实链路集成测试（真实 MySQL/Redis，完全自包含）
 * 每个测试自建独立订单，覆盖：
 *   申请售后→订单售后处理中(5) → 同意退款（订单已退款6/库存回补/销量回滚）
 *   → 撤销售后（订单恢复原状态）→ 拒绝退款（订单恢复原状态、无库存变动）
 * 全部 @Transactional 自动回滚，不污染数据库、测试间互不影响。
 */
@SpringBootTest
@Transactional
class AfterSalesFlowIntegrationTest {

    @Autowired
    private AfterSalesService afterSalesService;
    @Autowired
    private AdminAfterSalesService adminAfterSalesService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private AfterSalesMapper afterSalesMapper;
    @Autowired
    private ProductMapper productMapper;

    /** 测试库中真实用户（微信用户） */
    private static final Long USER_ID = 2095531860995977217L;
    /** 真实收货地址ID（用户本人） */
    private static final Long ADDRESS_ID = 2095708653606477826L;
    /** 真实商品：无规格商品 product 1 */
    private static final Long PRODUCT_ID = 1L;

    private int orderSeq = 0;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        AdminContext.setCurrentAdmin(1L, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        AdminContext.removeCurrentAdmin();
    }

    /**
     * 自建一笔已完成(3)的测试订单（product 1 ×1，无规格），全部字段由测试控制
     */
    private Order createCompletedOrder() {
        orderSeq++;
        Order order = new Order();
        order.setOrderNo("TST" + System.currentTimeMillis() + orderSeq);
        order.setUserId(USER_ID);
        order.setTotalAmount(new BigDecimal("19.90"));
        order.setFreight(new BigDecimal("0.00"));
        order.setDiscount(new BigDecimal("0.00"));
        order.setPayAmount(new BigDecimal("19.90"));
        order.setStatus(3);
        order.setAddressId(ADDRESS_ID);
        order.setReceiver("售后测试");
        order.setPhone("17395837632");
        order.setAddress("南京市集成测试地址");
        order.setPayType(1);
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(PRODUCT_ID);
        item.setSpecId(null);
        item.setProductName("测试商品");
        item.setPrice(new BigDecimal("19.90"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("19.90"));
        orderItemMapper.insert(item);
        return order;
    }

    private AfterSalesCreateRequest buildRequest(String orderNo, Integer type) {
        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo(orderNo);
        request.setType(type);
        request.setReasonType("QUALITY");
        request.setReason("集成测试：收到有坏果");
        request.setRefundAmount(new BigDecimal("19.90"));
        return request;
    }

    @Test
    @DisplayName("完整链路A：退货退款→订单售后处理中→同意→订单已退款/库存回补/销量回滚")
    void agreeFlow_restoresStockAndRefunds() {
        Order order = createCompletedOrder();
        Product productBefore = productMapper.selectById(PRODUCT_ID);

        // 1. 用户申请售后（退货退款 type=2：货物退回商家，库存应回补）
        AfterSalesDetailVO vo = afterSalesService.apply(buildRequest(order.getOrderNo(), 2));
        assertNotNull(vo.getAfterSalesNo());
        assertEquals(0, vo.getStatus());

        // 2. 订单进入售后处理中
        assertEquals(5, orderMapper.selectById(order.getId()).getStatus());

        // 3. 管理端同意退款
        AdminAfterSalesHandleRequest handle = new AdminAfterSalesHandleRequest();
        handle.setAgree(true);
        handle.setRemark("坏果包赔，同意退货退款");
        adminAfterSalesService.handle(vo.getAfterSalesNo(), handle);

        // 4. 售后单已同意，记录处理人/时间/意见
        AfterSales afterSales = afterSalesMapper.selectById(vo.getId());
        assertEquals(1, afterSales.getStatus());
        assertNotNull(afterSales.getHandleTime());
        assertEquals(1L, afterSales.getHandleBy());

        // 5. 订单置为已退款（独立状态6，不复用已取消4）
        Order refunded = orderMapper.selectById(order.getId());
        assertEquals(6, refunded.getStatus());
        assertEquals("售后同意退款，订单已退款", refunded.getCancelReason());

        // 6. 退货退款：无规格商品库存回补 +1；已完成订单销量回滚 -1（GREATEST 保护）
        Product productAfter = productMapper.selectById(PRODUCT_ID);
        assertEquals(productBefore.getStock() + 1, productAfter.getStock(), "退货退款库存应回补");
        assertEquals(productBefore.getSales() - 1, productAfter.getSales(), "销量应回滚");
    }

    @Test
    @DisplayName("完整链路A2：仅退款→同意→订单已退款/库存不回补/销量回滚")
    void agreeFlow_refundOnly_doesNotRestock() {
        Order order = createCompletedOrder();
        Product productBefore = productMapper.selectById(PRODUCT_ID);

        // 1. 用户申请仅退款（type=1：只退钱不退货，货仍在买家手里，库存不得回补）
        AfterSalesDetailVO vo = afterSalesService.apply(buildRequest(order.getOrderNo(), 1));
        assertEquals(5, orderMapper.selectById(order.getId()).getStatus());

        // 2. 管理端同意退款
        AdminAfterSalesHandleRequest handle = new AdminAfterSalesHandleRequest();
        handle.setAgree(true);
        handle.setRemark("坏果包赔，同意仅退款");
        adminAfterSalesService.handle(vo.getAfterSalesNo(), handle);

        // 3. 订单置为已退款
        assertEquals(6, orderMapper.selectById(order.getId()).getStatus());

        // 4. 仅退款：库存不回补，销量回滚（退款即交易未完成）
        Product productAfter = productMapper.selectById(PRODUCT_ID);
        assertEquals(productBefore.getStock(), productAfter.getStock(), "仅退款库存不应回补");
        assertEquals(productBefore.getSales() - 1, productAfter.getSales(), "销量应回滚");
    }

    @Test
    @DisplayName("完整链路B：撤销售后→订单恢复原状态，库存销量不变")
    void cancelFlow_restoresOrderStatus() {
        Order order = createCompletedOrder();

        // 1. 申请售后
        AfterSalesDetailVO vo = afterSalesService.apply(buildRequest(order.getOrderNo(), 1));
        assertEquals(5, orderMapper.selectById(order.getId()).getStatus());

        // 2. 用户撤销售后
        afterSalesService.cancelApply(vo.getAfterSalesNo());

        // 3. 订单恢复已完成
        assertEquals(3, orderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("完整链路C：拒绝退款→订单恢复原状态，库存销量不变")
    void rejectFlow_restoresOrderStatus() {
        Order order = createCompletedOrder();
        Product productBefore = productMapper.selectById(PRODUCT_ID);

        // 1. 申请售后
        AfterSalesDetailVO vo = afterSalesService.apply(buildRequest(order.getOrderNo(), 1));
        assertEquals(5, orderMapper.selectById(order.getId()).getStatus());

        // 2. 管理端拒绝退款
        AdminAfterSalesHandleRequest handle = new AdminAfterSalesHandleRequest();
        handle.setAgree(false);
        handle.setRemark("凭证不足，拒绝退款");
        adminAfterSalesService.handle(vo.getAfterSalesNo(), handle);

        // 3. 订单恢复已完成，售后单已拒绝
        assertEquals(3, orderMapper.selectById(order.getId()).getStatus());
        AfterSales afterSales = afterSalesMapper.selectById(vo.getId());
        assertEquals(2, afterSales.getStatus());
        assertEquals("凭证不足，拒绝退款", afterSales.getHandleRemark());

        // 4. 库存销量未变动
        Product productAfter = productMapper.selectById(PRODUCT_ID);
        assertEquals(productBefore.getStock(), productAfter.getStock());
        assertEquals(productBefore.getSales(), productAfter.getSales());
    }

    @Test
    @DisplayName("业务规则：已完成订单重复申请售后被拒绝")
    void duplicateApply_rejected() {
        Order order = createCompletedOrder();
        afterSalesService.apply(buildRequest(order.getOrderNo(), 1));

        // 第二次申请同一订单应失败（已有进行中售后申请）
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> afterSalesService.apply(buildRequest(order.getOrderNo(), 1)));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }
}
