package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.ProductSpec;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.*;

/**
 * 管理端售后模块单元测试：同意退款（订单关闭/库存回补/销量回滚）、拒绝退款
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

    @InjectMocks
    private AdminAfterSalesServiceImpl adminAfterSalesService;

    private static final Long ADMIN_ID = 1L;

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

    @Test
    @DisplayName("同意退款(退货退款)：已完成订单 → 订单已退款、规格/商品库存回补、销量回滚")
    void handle_agree_refundsStockAndSales() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3, 2));
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

        // 售后单置为已同意，记录处理人/时间/意见
        ArgumentCaptor<AfterSales> afsCaptor = ArgumentCaptor.forClass(AfterSales.class);
        verify(afterSalesMapper).updateById((AfterSales) afsCaptor.capture());        assertEquals(1, afsCaptor.getValue().getStatus());
        assertEquals(ADMIN_ID, afsCaptor.getValue().getHandleBy());
        assertEquals("坏果包赔，同意退款", afsCaptor.getValue().getHandleRemark());
        assertNotNull(afsCaptor.getValue().getHandleTime());
    }

    @Test
    @DisplayName("同意退款(仅退款)：已完成订单 → 不回补库存（货未退回），仅回滚销量")
    void handle_agree_refundOnly_doesNotRestock() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3, 1));
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
        when(orderMapper.selectById(1L)).thenReturn(order(5));

        AdminAfterSalesHandleRequest request = new AdminAfterSalesHandleRequest();
        request.setAgree(false);
        request.setRemark("证据不足，拒绝退款");
        adminAfterSalesService.handle("AFS10001", request);

        // 订单恢复待收货(2)
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) orderCaptor.capture());
        assertEquals(2, orderCaptor.getValue().getStatus());

        // 售后单置为已拒绝
        ArgumentCaptor<AfterSales> afsCaptor = ArgumentCaptor.forClass(AfterSales.class);
        verify(afterSalesMapper).updateById((AfterSales) afsCaptor.capture());
        assertEquals(2, afsCaptor.getValue().getStatus());

        // 拒绝不应回补库存
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
