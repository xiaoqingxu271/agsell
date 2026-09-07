package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CartMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.OrderCreateRequest;
import com.lichun.agsell.model.entity.Cart;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.model.entity.SysUserAddress;
import com.lichun.agsell.model.vo.OrderCreateVO;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 链路 A 修复验证：服务端计价、地址归属校验、库存口径（取消不恢复/收货只加销量）、超时取消
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;
    @Mock
    private SysUserAddressMapper addressMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Long USER_ID = 6L;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    // ==================== 辅助构造 ====================

    private SysUserAddress mockAddress(Long userId) {
        SysUserAddress addr = new SysUserAddress();
        addr.setId(100L);
        addr.setUserId(userId);
        addr.setReceiver("张三");
        addr.setPhone("13900139000");
        addr.setProvince("江西省");
        addr.setCity("赣州市");
        addr.setDistrict("章贡区");
        addr.setDetail("测试路1号");
        return addr;
    }

    private Product mockProduct(Long id, String name, BigDecimal price, Integer stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setSales(0);
        p.setMainImage("http://img/1.jpg");
        p.setStatus(1);
        return p;
    }

    private ProductSpec mockSpec(Long id, Long productId, String specName, BigDecimal price, Integer stock) {
        ProductSpec spec = new ProductSpec();
        spec.setId(id);
        spec.setProductId(productId);
        spec.setSpecName(specName);
        spec.setPrice(price);
        spec.setStock(stock);
        return spec;
    }

    /** 立即购买请求：前端伪造低价与商品名，验证服务端忽略 */
    private OrderCreateRequest buyNowRequest(Long productId, Long specId, Integer quantity) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(100L);
        OrderCreateRequest.OrderItemDTO dto = new OrderCreateRequest.OrderItemDTO();
        dto.setProductId(productId);
        dto.setSpecId(specId);
        dto.setQuantity(quantity);
        // 恶意前端字段：服务端必须忽略
        dto.setPrice(new BigDecimal("0.01"));
        dto.setSubtotal(new BigDecimal("0.01"));
        dto.setProductName("黑客改名");
        dto.setProductImage("http://evil/1.jpg");
        dto.setSpecName("黑客规格");
        req.setOrderItems(List.of(dto));
        return req;
    }

    // ==================== A1：立即购买服务端重新计价 ====================

    @Test
    @DisplayName("立即购买：忽略前端伪造价格/名称，按数据库重新计价")
    void createOrder_buyNow_recomputesPriceFromDb() {
        Product product = mockProduct(1L, "赣南脐橙", new BigDecimal("100.00"), 50);
        ProductSpec spec = mockSpec(2L, 1L, "5斤装", new BigDecimal("88.00"), 30);
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSpecMapper.selectById(2L)).thenReturn(spec);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderCreateVO vo = orderService.createOrder(buyNowRequest(1L, 2L, 2));

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).insert(captor.capture());
        OrderItem item = captor.getValue().get(0);

        // 价格必须来自规格表 88.00，而非前端 0.01
        assertEquals(0, new BigDecimal("88.00").compareTo(item.getPrice()));
        assertEquals(0, new BigDecimal("176.00").compareTo(item.getSubtotal()));
        // 快照必须来自数据库
        assertEquals("赣南脐橙", item.getProductName());
        assertEquals("http://img/1.jpg", item.getProductImage());
        assertEquals("5斤装", item.getSpecName());
        assertEquals(2L, item.getSpecId());
        // 订单号格式：AGS + 雪花
        assertNotNull(vo.getOrderNo());
        assertTrue(vo.getOrderNo().startsWith("AGS"));
        assertTrue(vo.getOrderNo().length() >= 20);
    }

    @Test
    @DisplayName("立即购买：无规格商品按商品售价计价")
    void createOrder_buyNow_withoutSpec_usesProductPrice() {
        Product product = mockProduct(1L, "土鸡蛋", new BigDecimal("12.50"), 100);
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        orderService.createOrder(buyNowRequest(1L, null, 3));

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).insert(captor.capture());
        OrderItem item = captor.getValue().get(0);
        assertEquals(0, new BigDecimal("12.50").compareTo(item.getPrice()));
        assertEquals(0, new BigDecimal("37.50").compareTo(item.getSubtotal()));
        assertNull(item.getSpecId());
    }

    @Test
    @DisplayName("立即购买：库存不足时下单失败")
    void createOrder_buyNow_stockInsufficient_throws() {
        Product product = mockProduct(1L, "赣南脐橙", new BigDecimal("100.00"), 50);
        ProductSpec spec = mockSpec(2L, 1L, "5斤装", new BigDecimal("88.00"), 3);
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSpecMapper.selectById(2L)).thenReturn(spec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyNowRequest(1L, 2L, 10)));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
        // 失败时不得创建订单
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("立即购买：规格不存在时下单失败")
    void createOrder_buyNow_specNotExist_throws() {
        Product product = mockProduct(1L, "赣南脐橙", new BigDecimal("100.00"), 50);
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSpecMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyNowRequest(1L, 99L, 1)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("立即购买：规格不属于该商品时下单失败")
    void createOrder_buyNow_specBelongsToOtherProduct_throws() {
        Product product = mockProduct(1L, "赣南脐橙", new BigDecimal("100.00"), 50);
        ProductSpec otherSpec = mockSpec(2L, 888L, "别的商品规格", new BigDecimal("1.00"), 99);
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSpecMapper.selectById(2L)).thenReturn(otherSpec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyNowRequest(1L, 2L, 1)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // ==================== A3：地址归属校验 ====================

    @Test
    @DisplayName("下单：地址不存在时报错（不允许空地址下单）")
    void createOrder_addressNotExist_throws() {
        when(addressMapper.selectById(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyNowRequest(1L, null, 1)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("下单：使用他人地址ID时报无权访问")
    void createOrder_addressNotOwned_throws() {
        when(addressMapper.selectById(100L)).thenReturn(mockAddress(999L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyNowRequest(1L, null, 1)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    // ==================== 购物车结算：服务端计价 + 删除条目 ====================

    @Test
    @DisplayName("购物车结算：服务端计价并删除已下单条目")
    void createOrder_cartFlow_recomputesAndDeletesCart() {
        Product product = mockProduct(1L, "赣南脐橙", new BigDecimal("100.00"), 50);
        ProductSpec spec = mockSpec(2L, 1L, "5斤装", new BigDecimal("88.00"), 30);
        Cart cart = new Cart();
        cart.setId(10L);
        cart.setUserId(USER_ID);
        cart.setProductId(1L);
        cart.setSpecId(2L);
        cart.setQuantity(2);
        cart.setSelected(1);

        when(addressMapper.selectById(100L)).thenReturn(mockAddress(USER_ID));
        when(cartMapper.selectList(any())).thenReturn(List.of(cart));
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSpecMapper.selectById(2L)).thenReturn(spec);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(100L);
        req.setCartItemIds(List.of(10L));
        OrderCreateVO vo = orderService.createOrder(req);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).insert(captor.capture());
        OrderItem item = captor.getValue().get(0);
        assertEquals(0, new BigDecimal("88.00").compareTo(item.getPrice()));
        assertEquals(0, new BigDecimal("176.00").compareTo(item.getSubtotal()));
        assertEquals(2L, item.getSpecId());
        // 已下单的购物车条目被删除
        verify(cartMapper).delete(any(Wrapper.class));
        assertNotNull(vo.getOrderNo());
    }

    // ==================== A2：库存口径 ====================

    @Test
    @DisplayName("取消待付款订单：不操作库存（库存仅在支付成功时扣减）")
    void cancelOrder_pending_doesNotTouchStock() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS123");
        order.setUserId(USER_ID);
        order.setStatus(0);
        when(orderMapper.selectOne(any())).thenReturn(order);

        orderService.cancelOrder("AGS123", "不想要了");

        // 不得调用任何库存更新
        verify(productMapper, never()).updateById(any(Product.class));
        verify(productMapper, never()).update(any(), any());
        // 状态更新为已取消
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) captor.capture());
        assertEquals(4, captor.getValue().getStatus());
        assertEquals("不想要了", captor.getValue().getCancelReason());
    }

    @Test
    @DisplayName("确认收货：只增加销量，不再扣减库存")
    void confirmReceive_onlyIncrementsSales() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("AGS123");
        order.setUserId(USER_ID);
        order.setStatus(2);
        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setProductId(1L);
        item.setQuantity(2);

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(productMapper.update(any(), any())).thenReturn(1);

        orderService.confirmReceive("AGS123");

        // 库存扣减必须发生在支付时，收货不再更新库存
        verify(productMapper, never()).updateById(any(Product.class));
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(productMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSet().contains("sales = sales + 2"));
        assertFalse(wrapperCaptor.getValue().getSqlSet().toLowerCase().contains("stock"));
        // 订单状态更新为已完成
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) orderCaptor.capture());
        assertEquals(3, orderCaptor.getValue().getStatus());
    }

    // ==================== A4：超时自动取消 ====================

    @Test
    @DisplayName("超时自动取消：取消超过阈值的待付款订单")
    void cancelExpiredOrders_cancelsExpiredOrders() {
        Order expired = new Order();
        expired.setId(1L);
        expired.setOrderNo("AGS111");
        expired.setStatus(0);
        expired.setCreateTime(LocalDateTime.now().minusHours(2));
        when(orderMapper.selectList(any())).thenReturn(List.of(expired));

        int count = orderService.cancelExpiredOrders(30);

        assertEquals(1, count);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById((Order) captor.capture());
        assertEquals(4, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCancelReason());
    }

    @Test
    @DisplayName("超时自动取消：无超时订单时返回 0")
    void cancelExpiredOrders_noExpired_returnsZero() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        int count = orderService.cancelExpiredOrders(30);

        assertEquals(0, count);
        verify(orderMapper, never()).updateById(any(Order.class));
    }
}
