package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.model.dto.AfterSalesCreateRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.vo.AfterSalesDetailVO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 售后模块单元测试：申请售后、订单状态联动、撤销售后
 */
@ExtendWith(MockitoExtension.class)
class AfterSalesServiceImplTest {

    @Mock
    private AfterSalesMapper afterSalesMapper;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private AfterSalesServiceImpl afterSalesService;

    private static final Long USER_ID = 6L;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private Order receivableOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS001");
        order.setUserId(USER_ID);
        order.setStatus(2); // 待收货
        order.setPayAmount(new BigDecimal("59.90"));
        return order;
    }

    private AfterSales pendingAfterSales(Integer originalStatus) {
        AfterSales afterSales = new AfterSales();
        afterSales.setId(10L);
        afterSales.setAfterSalesNo("AFS10001");
        afterSales.setOrderId(1L);
        afterSales.setOrderNo("AGS001");
        afterSales.setUserId(USER_ID);
        afterSales.setOriginalStatus(originalStatus);
        afterSales.setType(1);
        afterSales.setReasonType("QUALITY");
        afterSales.setRefundAmount(new BigDecimal("59.90"));
        afterSales.setStatus(0);
        return afterSales;
    }

    @Test
    @DisplayName("申请售后：待收货订单申请成功，售后单创建且订单进入售后处理中")
    void apply_success_setsOrderAfterSales() {
        when(orderMapper.selectOne(any())).thenReturn(receivableOrder());
        when(afterSalesMapper.selectCount(any())).thenReturn(0L);

        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo("AGS001");
        request.setType(1);
        request.setReasonType("QUALITY");
        request.setReason("收到有坏果");
        request.setRefundAmount(new BigDecimal("59.90"));

        AfterSalesDetailVO vo = afterSalesService.apply(request);

        assertNotNull(vo.getAfterSalesNo());
        assertTrue(vo.getAfterSalesNo().startsWith("AFS"));
        assertEquals(0, BigDecimal.valueOf(59.90).compareTo(vo.getRefundAmount()));

        // 售后单落库，状态待处理
        ArgumentCaptor<AfterSales> afsCaptor = ArgumentCaptor.forClass(AfterSales.class);
        verify(afterSalesMapper).insert((AfterSales) afsCaptor.capture());
        assertEquals(0, afsCaptor.getValue().getStatus());
        assertEquals(2, afsCaptor.getValue().getOriginalStatus());

        // 订单更新为售后处理中(5)
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) orderCaptor.capture());
        assertEquals(5, orderCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("申请售后：非本人订单（查询按 userId 过滤返回空）拒绝")
    void apply_rejectsOthersOrder() {
        // 模拟 SQL 按 用户+订单号 过滤：非本人订单查不到
        when(orderMapper.selectOne(any())).thenReturn(null);

        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo("AGS001");
        request.setType(1);
        request.setReasonType("OTHER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.apply(request));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("申请售后：待付款订单不可申请")
    void apply_rejectsPendingPayOrder() {
        Order order = receivableOrder();
        order.setStatus(0); // 待付款
        when(orderMapper.selectOne(any())).thenReturn(order);

        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo("AGS001");
        request.setType(1);
        request.setReasonType("OTHER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.apply(request));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("申请售后：退款金额不能超过实付金额")
    void apply_rejectsRefundOverPay() {
        when(orderMapper.selectOne(any())).thenReturn(receivableOrder());

        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo("AGS001");
        request.setType(1);
        request.setReasonType("OTHER");
        request.setRefundAmount(new BigDecimal("999"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.apply(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(afterSalesMapper, never()).insert(any(AfterSales.class));
    }

    @Test
    @DisplayName("申请售后：同一订单已有进行中售后单时拒绝重复申请")
    void apply_rejectsDuplicatePending() {
        when(orderMapper.selectOne(any())).thenReturn(receivableOrder());
        when(afterSalesMapper.selectCount(any())).thenReturn(1L);

        AfterSalesCreateRequest request = new AfterSalesCreateRequest();
        request.setOrderNo("AGS001");
        request.setType(1);
        request.setReasonType("OTHER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.apply(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        verify(afterSalesMapper, never()).insert(any(AfterSales.class));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("撤销售后：待处理可撤销，订单恢复申请前状态")
    void cancelApply_restoresOrderStatus() {
        when(afterSalesMapper.selectOne(any())).thenReturn(pendingAfterSales(3));

        afterSalesService.cancelApply("AFS10001");

        // 售后单置为已撤销
        ArgumentCaptor<AfterSales> afsCaptor = ArgumentCaptor.forClass(AfterSales.class);
        verify(afterSalesMapper).updateById((AfterSales) afsCaptor.capture());
        assertEquals(3, afsCaptor.getValue().getStatus());

        // 订单从售后处理中(5)恢复为已完成(3)
        verify(orderMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("撤销售后：已处理的售后单不可撤销")
    void cancelApply_rejectsHandled() {
        AfterSales handled = pendingAfterSales(2);
        handled.setStatus(1); // 已同意
        when(afterSalesMapper.selectOne(any())).thenReturn(handled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.cancelApply("AFS10001"));
        assertEquals(ErrorCode.AFTER_SALES_STATUS_ERROR.getCode(), ex.getCode());
        verify(afterSalesMapper, never()).updateById(any(AfterSales.class));
    }

    @Test
    @DisplayName("售后详情：非本人的售后单（查询按 userId 过滤返回空）不可见")
    void getDetail_rejectsOthersAfterSales() {
        // 模拟 SQL 按 用户+售后单号 过滤：非本人售后单查不到
        when(afterSalesMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> afterSalesService.getDetail("AFS10001"));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }
}
