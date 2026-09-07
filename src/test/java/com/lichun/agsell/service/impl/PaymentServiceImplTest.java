package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 链路 A 修复验证：支付成功时乐观锁扣减库存（有规格扣规格库存，无规格扣商品库存）
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final Long USER_ID = 6L;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS123");
        order.setUserId(USER_ID);
        order.setStatus(0);
        return order;
    }

    private OrderItem item(Long productId, Long specId, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setProductId(productId);
        item.setSpecId(specId);
        item.setQuantity(quantity);
        item.setProductName("赣南脐橙");
        return item;
    }

    @Test
    @DisplayName("支付成功：有规格商品扣减规格库存（乐观锁条件 stock >= quantity）")
    void createPayment_deductsSpecStock() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 2)));
        when(productSpecMapper.update(any(), any())).thenReturn(1);

        paymentService.createPayment("AGS123");

        // 规格库存被扣减，商品库存不动
        verify(productSpecMapper).update(any(), any());
        verify(productMapper, never()).update(any(), any());
        // 订单状态更新为已支付（待发货）
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) captor.capture());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("支付成功：无规格商品扣减商品总库存")
    void createPayment_deductsProductStock_whenNoSpec() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, null, 3)));
        when(productMapper.update(any(), any())).thenReturn(1);

        paymentService.createPayment("AGS123");

        verify(productMapper).update(any(), any());
        verify(productSpecMapper, never()).update(any(), any());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) captor.capture());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("支付失败：规格库存不足时抛异常，订单状态不更新（事务回滚）")
    void createPayment_specStockInsufficient_throwsAndStatusUnchanged() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 99)));
        when(productSpecMapper.update(any(), any())).thenReturn(0); // 乐观锁扣减失败

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123"));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
        // 状态更新必须在库存扣减成功之后，失败时不得更新订单
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("支付失败：商品库存不足时抛异常，订单状态不更新")
    void createPayment_productStockInsufficient_throws() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, null, 99)));
        when(productMapper.update(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123"));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("支付：订单不是待付款状态时拒绝支付")
    void createPayment_orderStatusNotPending_throws() {
        Order paid = pendingOrder();
        paid.setStatus(1);
        when(orderMapper.selectOne(any())).thenReturn(paid);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123"));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(orderItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("支付：订单不存在时抛异常")
    void createPayment_orderNotExist_throws() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123"));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }
}
