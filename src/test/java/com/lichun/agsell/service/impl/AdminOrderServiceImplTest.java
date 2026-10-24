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
import com.lichun.agsell.service.UserMessageService;
import com.lichun.agsell.support.MpLambdaCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * 链路 B 修复验证：发货请求判空、状态校验与并发发货原子抢占
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private UserMessageService userMessageService;

    @InjectMocks
    private AdminOrderServiceImpl adminOrderService;

    @BeforeAll
    static void initLambdaCache() {
        // 发货用 LambdaUpdateWrapper.set(...) 写状态与物流单号，链式调用时即解析列名
        MpLambdaCache.init(Order.class);
    }

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
        order.setUserId(9L);
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
    @DisplayName("正常发货：原子抢占「待发货→待收货」，条件 UPDATE 生效")
    void shipOrderSuccess() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(1));
        when(orderMapper.update(nullable(Order.class), any())).thenReturn(1);

        OrderShipRequest request = new OrderShipRequest();
        request.setLogType("顺丰速运");
        request.setLogNo("SF123456");
        adminOrderService.shipOrder("AGS123", request);

        // 状态、物流单号、发货时间一次写入；不再有「先查后无条件改」的 updateById
        verify(orderMapper).update(nullable(Order.class), any());
        verify(orderMapper, never()).updateById(any(Order.class));

        // 发货结果写站内信：用户在消息中心能看到（与发货同事务）
        verify(userMessageService).notify(eq(9L), eq("order"), eq("订单已发货"),
                contains("SF123456"), eq("AGS123"));
    }

    @Test
    @DisplayName("并发发货：条件 UPDATE 影响 0 行（已被他人发货）时报状态错误，不覆盖物流单号")
    void shipOrderLosingRaceFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(1));
        when(orderMapper.update(nullable(Order.class), any())).thenReturn(0);

        OrderShipRequest request = new OrderShipRequest();
        request.setLogType("顺丰速运");
        request.setLogNo("SF999999");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminOrderService.shipOrder("AGS123", request));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).updateById(any(Order.class));
        // 抢占失败不发货，也不应有站内信
        verify(userMessageService, never()).notify(any(), any(), any(), any(), any());
    }
}
