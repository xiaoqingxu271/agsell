package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 支付边界与并发安全测试：
 * 支付权原子抢占（条件 UPDATE 待付款→待发货）+ 乐观锁扣库存。
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

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 单测没有 MyBatis-Plus 运行时，手动初始化 lambda 列缓存，
        // 否则生产代码里 LambdaUpdateWrapper 的 Order::getId 等解析会抛
        // "can not find lambda cache for this entity"
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, ProductSpec.class);
    }

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

    /** 抢占支付权的条件 UPDATE（Mockito 下默认返回 0，成功路径需显式打桩返回 1） */
    private void claimSucceeds() {
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedClaimParams() {
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        return captor.getValue().getParamNameValuePairs();
    }

    @Test
    @DisplayName("支付成功：原子抢占支付权 + 有规格商品扣减规格库存（乐观锁）")
    void createPayment_deductsSpecStock() {
        claimSucceeds();
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 2)));
        when(productSpecMapper.update(any(), any())).thenReturn(1);

        paymentService.createPayment("AGS123", 1);

        // 规格库存被扣减，商品库存不动
        verify(productSpecMapper).update(any(), any());
        verify(productMapper, never()).update(any(), any());
        // 抢占 UPDATE 携带目标状态与支付方式
        Map<String, Object> params = capturedClaimParams();
        assertTrue(params.containsValue(OrderStatusEnum.PENDING_SHIPMENT.getCode()));
        assertTrue(params.containsValue(1)); // 支付宝
    }

    @Test
    @DisplayName("支付成功：无规格商品扣减商品总库存，微信支付方式落库")
    void createPayment_deductsProductStock_whenNoSpec() {
        claimSucceeds();
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, null, 3)));
        when(productMapper.update(any(), any())).thenReturn(1);

        paymentService.createPayment("AGS123", 2);

        verify(productMapper).update(any(), any());
        verify(productSpecMapper, never()).update(any(), any());
        Map<String, Object> params = capturedClaimParams();
        assertTrue(params.containsValue(2)); // 微信支付
    }

    @Test
    @DisplayName("支付成功：未传支付方式时默认按支付宝落库")
    void createPayment_defaultPayType_whenNull() {
        claimSucceeds();
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 1)));
        when(productSpecMapper.update(any(), any())).thenReturn(1);

        paymentService.createPayment("AGS123", null);

        Map<String, Object> params = capturedClaimParams();
        assertTrue(params.containsValue(1)); // 默认支付宝
    }

    @Test
    @DisplayName("并发重复支付：抢占失败（条件 UPDATE 返回0）→ 抛请勿重复支付，不触达库存")
    void createPayment_claimFailed_throwsAndNoStockDeduction() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder()); // 状态读到待付款
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0); // 抢占失败（已被并发请求支付）

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 1));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        // 关键：抢占失败必须终止在扣库存之前
        verify(orderItemMapper, never()).selectList(any());
        verify(productMapper, never()).update(any(), any());
        verify(productSpecMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("支付失败：不支持的支付方式时抛参数错误")
    void createPayment_invalidPayType_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 9));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).selectOne(any());
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("支付失败：规格库存不足时抛异常（事务回滚含已抢占的状态变更）")
    void createPayment_specStockInsufficient_throwsAndStatusUnchanged() {
        claimSucceeds();
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, 2L, 99)));
        when(productSpecMapper.update(any(), any())).thenReturn(0); // 乐观锁扣减失败

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 1));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
        // 失败路径依赖 @Transactional 回滚抢占变更；单测层面验证库存扣减确实被尝试过
        verify(productSpecMapper).update(any(), any());
    }

    @Test
    @DisplayName("支付失败：商品库存不足时抛异常")
    void createPayment_productStockInsufficient_throws() {
        claimSucceeds();
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(1L, null, 99)));
        when(productMapper.update(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 1));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("支付：订单不是待付款状态时拒绝支付")
    void createPayment_orderStatusNotPending_throws() {
        Order paid = pendingOrder();
        paid.setStatus(1);
        when(orderMapper.selectOne(any())).thenReturn(paid);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 1));
        assertEquals(ErrorCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).update(isNull(), any());
        verify(orderItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("支付：订单不存在时抛异常")
    void createPayment_orderNotExist_throws() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment("AGS123", 1));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }
}
