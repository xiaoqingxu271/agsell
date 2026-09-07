package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.OrderShipRequest;
import com.lichun.agsell.model.entity.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 链路 B 修复验证：发货请求判空与状态校验
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private AdminOrderServiceImpl adminOrderService;

    @BeforeEach
    void setUp() {
        AdminContext.setCurrentAdmin(1L, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        AdminContext.removeCurrentAdmin();
    }

    private Order buildOrder(int status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS" + "2096878950334377984");
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("请求体为空时报参数错误")
    void shipWithNullRequestFails() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminOrderService.shipOrder("AGS123", null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("物流公司为空时报参数错误")
    void shipWithBlankLogTypeFails() {
        OrderShipRequest request = new OrderShipRequest();
        request.setLogType(" ");
        request.setLogNo("SF123");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminOrderService.shipOrder("AGS123", request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("订单不存在时报错")
    void shipNonExistentOrderFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        OrderShipRequest request = new OrderShipRequest();
        request.setLogType("顺丰速运");
        request.setLogNo("SF123456");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminOrderService.shipOrder("AGS123", request));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非待发货订单发货报状态错误")
    void shipNonPendingOrderFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));

        OrderShipRequest request = new OrderShipRequest();
        request.setLogType("顺丰速运");
        request.setLogNo("SF123456");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminOrderService.shipOrder("AGS123", request));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常发货更新状态为待收货并记录物流")
    void shipOrderSuccess() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(1));
        when(orderMapper.updateById((Order) any())).thenReturn(1);

        OrderShipRequest request = new OrderShipRequest();
        request.setLogType("顺丰速运");
        request.setLogNo("SF123456");
        adminOrderService.shipOrder("AGS123", request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        Order update = captor.getValue();
        assertEquals(2, update.getStatus());
        assertEquals("顺丰速运", update.getLogType());
        assertEquals("SF123456", update.getLogNo());
        assertNotNull(update.getDeliveryTime());
    }
}
