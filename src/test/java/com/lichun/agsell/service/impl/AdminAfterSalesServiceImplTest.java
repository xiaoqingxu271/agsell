package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.support.MpLambdaCache;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.service.UserMessageService;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.ProductSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * 管理端售后模块单元测试：同意退款（订单关闭/库存回补/销量回滚）、拒绝退款、处理权原子抢占
 */
@ExtendWith(MockitoExtension.class)
class AdminAfterSalesServiceImplTest {

    @Mock
    private AfterSalesMapper afterSalesMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;
    @Mock
    private UserMessageService userMessageService;

    @InjectMocks
    private AdminAfterSalesServiceImpl adminAfterSalesService;

    private static final Long ADMIN_ID = 1L;

    @BeforeAll
    static void initLambdaCache() {
        // 售后处理用 LambdaUpdateWrapper.set(...) 写状态，链式调用时即解析列名
        MpLambdaCache.init(AfterSales.class);
    }

    @BeforeEach
    void setUp() {
        AdminContext.setCurrentAdmin(ADMIN_ID, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        AdminContext.removeCurrentAdmin();
    }

    private AfterSales pendingAfterSales(Integer originalStatus, Integer type) {
        AfterSales afterSales = new AfterSales();
        afterSales.setId(10L);
        afterSales.setAfterSalesNo("AFS10001");
        afterSales.setOrderId(1L);
        afterSales.setOrderNo("AGS001");
        afterSales.setUserId(6L);
        afterSales.setOriginalStatus(originalStatus);
        afterSales.setType(type);
        afterSales.setReasonType("QUALITY");
        afterSales.setRefundAmount(new BigDecimal("59.90"));
        afterSales.setStatus(0);
        return afterSales;
    }

    private Order order(int status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS001");
        order.setStatus(status);
        order.setPayAmount(new BigDecimal("59.90"));
        return order;
    }

    private OrderItem item(Long productId, Long specId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setProductId(productId);
        item.setSpecId(specId);
        item.setQuantity(quantity);
        item.setProductName("赣南脐橙");
        return item;
    }

    /**
     * 模拟「处理权原子抢占」成功：条件 UPDATE（待处理 → 已处理）影响 1 行。
     * 不 stub 时 Mockito 返回 0，业务侧会视为抢不到处理权并直接拒绝。
     */
    private void mockClaimSuccess() {
        when(afterSalesMapper.update(nullable(AfterSales.class), any())).thenReturn(1);
    }

    @Test
    @DisplayName("同意退款(退货退款)：已完成订单 → 订单已退款、规格/商品库存回补、销量回滚")
    void handle_agree_refundsStockAndSales() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3, 2));
        mockClaimSuccess();
        when(orderMapper.selectById(1L)).thenReturn(order(5));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 2L, 2),
                item(3L, null, 1)
        ));
        when(productSpecMapper.update(any(), any())).thenReturn(1);
        when(productMapper.update(any(), any())).thenReturn(1);

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(true);
        request.setRemark("坏果包赔，同意退款");
        adminAfterSalesService.handle("AFS10001", request);

        // 退货退款：规格库存回补 + 无规格商品库存回补 + 两个商品销量回滚 = 3 次商品更新
        verify(productSpecMapper).update(any(), any());
        verify(productMapper, times(3)).update(any(), any());

        // 订单置为已退款(6)，并记录原因
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) orderCaptor.capture());
        assertEquals(6, orderCaptor.getValue().getStatus());
        assertEquals("售后同意退款，订单已退款", orderCaptor.getValue().getCancelReason());

        // 售后单结果由原子抢占的条件 UPDATE 一次写入（状态/处理人/时间/意见），不再二次 updateById
        verify(afterSalesMapper).update(nullable(AfterSales.class), any());
        verify(afterSalesMapper, never()).updateById(any(AfterSales.class));

        // 售后结果写站内信：用户在消息中心能看到同意退款通知
        verify(userMessageService).notify(eq(6L), eq("after_sales"), eq("售后已同意退款"),
                contains("¥59.90"), eq("AFS10001"));
    }

    @Test
    @DisplayName("同意退款(仅退款)：已完成订单 → 不回补库存（货未退回），仅回滚销量")
    void handle_agree_refundOnly_doesNotRestock() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3, 1));
        mockClaimSuccess();
        when(orderMapper.selectById(1L)).thenReturn(order(5));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 2L, 2),
                item(3L, null, 1)
        ));
        when(productMapper.update(any(), any())).thenReturn(1);

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(true);
        adminAfterSalesService.handle("AFS10001", request);

        // 仅退款：库存一律不回补
        verify(productSpecMapper, never()).update(any(), any());
        // 销量回滚：两个商品各一次（规格商品按 productId 回滚）
        verify(productMapper, times(2)).update(any(), any());
    }

    @Test
    @DisplayName("同意退款(退货退款)：待收货订单（未确认收货）回补库存，不扣减销量")
    void handle_agree_noSalesRollbackForReceivable() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(2, 2));
        mockClaimSuccess();
        when(orderMapper.selectById(1L)).thenReturn(order(5));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 2)));

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(true);
        adminAfterSalesService.handle("AFS10001", request);

        // 退货退款：有规格回补规格库存一次
        verify(productSpecMapper).update(any(), any());
        // 原状态为待收货，销量未累加过，不应回滚商品销量
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("拒绝退款：售后单置为已拒绝，订单恢复申请前状态")
    void handle_reject_restoresOrderStatus() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(2, 1));
        mockClaimSuccess();
        when(orderMapper.selectById(1L)).thenReturn(order(5));

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(false);
        request.setRemark("证据不足，拒绝退款");
        adminAfterSalesService.handle("AFS10001", request);

        // 订单恢复待收货(2)
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) orderCaptor.capture());
        assertEquals(2, orderCaptor.getValue().getStatus());

        // 售后单已拒绝：由原子抢占的条件 UPDATE 写入，不再二次 updateById
        verify(afterSalesMapper).update(nullable(AfterSales.class), any());
        verify(afterSalesMapper, never()).updateById(any(AfterSales.class));

        // 拒绝不应回补库存
        verify(productSpecMapper, never()).update(any(), any());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("并发处理同一售后单：抢占失败方（影响 0 行）直接拒绝，不产生任何回补副作用")
    void handle_losingRace_hasNoSideEffects() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3, 2));
        // 条件 UPDATE 影响 0 行 = 另一位管理员已先一步把状态从「待处理」改走
        when(afterSalesMapper.update(nullable(AfterSales.class), any())).thenReturn(0);

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminAfterSalesService.handle("AFS10001", request));
        assertEquals(ErrorCode.AFTER_SALES_STATUS_ERROR.getCode(), ex.getCode());

        // 库存回补 / 销量回滚 / 订单状态全部不得发生，避免重复退款
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(productSpecMapper, never()).update(any(), any());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("处理售后：已处理的售后单禁止重复操作")
    void handle_rejectsHandledAfterSales() {
        AfterSales handled = pendingAfterSales(3, 1);
        handled.setStatus(1);
        when(afterSalesMapper.selectOne(any())).thenReturn(handled);

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminAfterSalesService.handle("AFS10001", request));
        assertEquals(ErrorCode.AFTER_SALES_STATUS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(afterSalesMapper, never()).updateById(any(AfterSales.class));
        verify(productSpecMapper, never()).update(any(), any());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("处理售后：未选择处理结果时拒绝（查询前校验）")
    void handle_requiresAgreeFlag() {
        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminAfterSalesService.handle("AFS10001", request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(afterSalesMapper, never()).selectOne(any());
    }
}
