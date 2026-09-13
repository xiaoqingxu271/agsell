package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SeckillActivityMapper;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.model.dto.SeckillActivityRequest;
import com.lichun.agsell.model.dto.SeckillOrderRequest;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.SeckillActivity;
import com.lichun.agsell.model.entity.SysUserAddress;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.SeckillDetailVO;
import com.lichun.agsell.service.SeckillRedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 秒杀模块单元测试：活动校验、Lua 预扣、一人一单、失败补偿、释放幂等
 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper productSpecMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SysUserAddressMapper addressMapper;
    @Mock
    private SeckillRedisService seckillRedisService;

    @InjectMocks
    private SeckillServiceImpl seckillService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seckillService, "timeoutMinutes", 30);
        BaseContext.setCurrentId(1L, "test-jti");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private SeckillActivity mockActivity(int status, LocalDateTime start, LocalDateTime end, int stock) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(101L);
        activity.setActivityCode("TESTCODE101");
        activity.setProductId(1001L);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setSeckillStock(stock);
        activity.setSeckillLimit(1);
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setStatus(status);
        return activity;
    }

    private SeckillActivity ongoingActivity() {
        return mockActivity(1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 50);
    }

    private Product mockProduct() {
        Product product = new Product();
        product.setId(1001L);
        product.setName("赣南脐橙");
        product.setMainImage("https://oss.example.com/main.png");
        product.setPrice(new BigDecimal("39.90"));
        product.setStatus(1);
        product.setStock(100);
        return product;
    }

    private SysUserAddress mockAddress() {
        SysUserAddress address = new SysUserAddress();
        address.setId(10L);
        address.setUserId(1L);
        address.setReceiver("张三");
        address.setPhone("13800000000");
        address.setProvince("江西省");
        address.setCity("赣州市");
        address.setDistrict("章贡区");
        address.setDetail("xx路xx号");
        return address;
    }

    private SeckillOrderRequest buildOrderRequest() {
        SeckillOrderRequest request = new SeckillOrderRequest();
        request.setActivityCode("TESTCODE101");
        request.setAddressId(10L);
        return request;
    }

    /** 按活动编号定位活动（对外 code → 内部主键） */
    private void mockActivityLookup(SeckillActivity activity) {
        when(seckillActivityMapper.selectOne(any())).thenReturn(activity);
    }

    @Test
    @DisplayName("秒杀下单成功：Redis 预扣 + 创建待付款订单，金额为秒杀价")
    void createOrder_success() {
        SeckillActivity activity = ongoingActivity();
        mockActivityLookup(activity);
        when(seckillRedisService.getSnapshot(101L)).thenReturn(activity);
        when(seckillRedisService.trySeckill(101L, 1L)).thenReturn(1);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());
        // mock insert：模拟 MyBatis-Plus 主键回填（doAnswer 规避 BaseMapper insert 重载歧义）
        doAnswer(inv -> {
            inv.getArgument(0, Order.class).setId(999L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));

        OrderCreateVO vo = seckillService.createSeckillOrder(buildOrderRequest());

        assertNotNull(vo);
        assertEquals(new BigDecimal("9.90"), vo.getPayAmount());
        assertEquals(0, vo.getStatus()); // 待付款
        // 捕获订单参数：一人一单标记 + 秒杀价
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert((Order) captor.capture());
        assertEquals(101L, captor.getValue().getSeckillActivityId());
        assertEquals(new BigDecimal("9.90"), captor.getValue().getPayAmount());
        assertEquals(1L, captor.getValue().getUserId());
        verify(orderItemMapper).insert(any(OrderItem.class));
        verify(seckillRedisService, never()).releaseSeckill(any(), any());
    }

    @Test
    @DisplayName("未开始下单：SECKILL_NOT_STARTED")
    void createOrder_notStarted() {
        SeckillActivity activity = mockActivity(1, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), 50);
        mockActivityLookup(activity);
        when(seckillRedisService.getSnapshot(101L)).thenReturn(activity);
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_NOT_STARTED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已结束下单：SECKILL_ENDED")
    void createOrder_ended() {
        SeckillActivity activity = mockActivity(1, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), 50);
        mockActivityLookup(activity);
        when(seckillRedisService.getSnapshot(101L)).thenReturn(activity);
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_ENDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("活动已下架：SECKILL_ENDED")
    void createOrder_disabled() {
        SeckillActivity activity = ongoingActivity();
        activity.setStatus(0);
        mockActivityLookup(activity);
        when(seckillRedisService.getSnapshot(101L)).thenReturn(activity);
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_ENDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("库存为0：SECKILL_SOLD_OUT")
    void createOrder_soldOut() {
        mockActivityLookup(ongoingActivity());
        when(seckillRedisService.getSnapshot(101L)).thenReturn(ongoingActivity());
        when(seckillRedisService.trySeckill(101L, 1L)).thenReturn(0);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_SOLD_OUT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("同用户重复抢购（Redis 标记）：SECKILL_REPEAT")
    void createOrder_repeatByRedis() {
        mockActivityLookup(ongoingActivity());
        when(seckillRedisService.getSnapshot(101L)).thenReturn(ongoingActivity());
        when(seckillRedisService.trySeckill(101L, 1L)).thenReturn(-2);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_REPEAT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("DB 唯一索引冲突（并发穿透 Redis 标记）：SECKILL_REPEAT + 名额补偿")
    void createOrder_repeatByDbUniqueKey() {
        mockActivityLookup(ongoingActivity());
        when(seckillRedisService.getSnapshot(101L)).thenReturn(ongoingActivity());
        when(seckillRedisService.trySeckill(101L, 1L)).thenReturn(1);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());
        doThrow(new DuplicateKeyException("uk_user_seckill"))
                .when(orderMapper).insert(any(Order.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        assertEquals(ErrorCode.SECKILL_REPEAT.getCode(), ex.getCode());
        // 名额必须回补
        verify(seckillRedisService).releaseSeckill(101L, 1L);
    }

    @Test
    @DisplayName("下单 DB 异常：回补 Redis 名额 + 异常传播")
    void createOrder_dbErrorCompensate() {
        mockActivityLookup(ongoingActivity());
        when(seckillRedisService.getSnapshot(101L)).thenReturn(ongoingActivity());
        when(seckillRedisService.trySeckill(101L, 1L)).thenReturn(1);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(addressMapper.selectById(10L)).thenReturn(mockAddress());
        doThrow(new RuntimeException("db down")).when(orderMapper).insert(any(Order.class));

        assertThrows(RuntimeException.class,
                () -> seckillService.createSeckillOrder(buildOrderRequest()));
        verify(seckillRedisService).releaseSeckill(101L, 1L);
    }

    @Test
    @DisplayName("释放名额：幂等委托 Redis 服务")
    void releaseQuota() {
        seckillService.releaseSeckillQuota(101L, 1L);
        verify(seckillRedisService).releaseSeckill(101L, 1L);
    }

    @Test
    @DisplayName("创建活动：秒杀价必须低于现价")
    void createActivity_priceMustLower() {
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(1001L);
        request.setSeckillPrice(new BigDecimal("50.00")); // 高于现价 39.90
        request.setSeckillStock(10);
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createActivity(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建活动：秒杀库存不能超过商品库存")
    void createActivity_stockLimit() {
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(1001L);
        request.setSeckillPrice(new BigDecimal("9.90"));
        request.setSeckillStock(200); // 商品库存 100
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createActivity(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建活动：同一商品时间区间重叠被拦截")
    void createActivity_overlapRejected() {
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(1001L);
        request.setSeckillPrice(new BigDecimal("9.90"));
        request.setSeckillStock(10);
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(seckillActivityMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.createActivity(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建活动成功：写入快照 + 生成不可枚举活动编号")
    void createActivity_success() {
        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setProductId(1001L);
        request.setSeckillPrice(new BigDecimal("9.90"));
        request.setSeckillStock(10);
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(seckillActivityMapper.selectCount(any())).thenReturn(0L);
        final SeckillActivity[] inserted = new SeckillActivity[1];
        when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenAnswer(inv -> {
            inserted[0] = inv.getArgument(0);
            inserted[0].setId(101L);
            return 1;
        });

        Long id = seckillService.createActivity(request);
        assertEquals(101L, id);
        // 活动编号：16 位大写字母数字，不可枚举
        assertNotNull(inserted[0].getActivityCode());
        assertEquals(16, inserted[0].getActivityCode().length());
        assertTrue(inserted[0].getActivityCode().matches("[A-Z0-9]{16}"));
        verify(seckillRedisService).saveSnapshot(any(SeckillActivity.class));
    }

    @Test
    @DisplayName("活动详情：按对外编号定位，返回秒杀信息")
    void getActivityDetail_byCode() {
        SeckillActivity activity = ongoingActivity();
        when(seckillActivityMapper.selectOne(any())).thenReturn(activity);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(seckillRedisService.getRemainingStock(101L)).thenReturn(50);
        when(seckillRedisService.hasUserSeckilled(101L, 1L)).thenReturn(false);

        SeckillDetailVO vo = seckillService.getActivityDetail("TESTCODE101");

        assertNotNull(vo);
        assertEquals("TESTCODE101", vo.getActivityCode());
        assertEquals(new BigDecimal("9.90"), vo.getSeckillPrice());
        assertEquals(2, vo.getActivityStatus()); // 进行中
        // 未登录用户 userSeckilled=false
        assertFalse(vo.getUserSeckilled());
    }

    @Test
    @DisplayName("编辑已开始活动：禁止修改库存与时间")
    void updateActivity_startedRejected() {
        SeckillActivity activity = ongoingActivity();
        when(seckillActivityMapper.selectById(101L)).thenReturn(activity);

        SeckillActivityRequest request = new SeckillActivityRequest();
        request.setSeckillStock(100);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.updateActivity(101L, request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("列表过滤：进行中活动返回正确状态与倒计时")
    void listActivities_ongoingOnly() {
        SeckillActivity ongoing = ongoingActivity();
        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(ongoing));
        when(seckillRedisService.getRemainingStock(101L)).thenReturn(50);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());

        var list = seckillService.listActivities(1);
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).getActivityStatus()); // 进行中
        assertTrue(list.get(0).getCountdownSeconds() > 0);
        assertEquals(0, list.get(0).getProgress()); // 未售出
    }

    @Test
    @DisplayName("列表过滤：filter=1 时未开始活动被过滤")
    void listActivities_filterExcludesNotStarted() {
        SeckillActivity notStarted = mockActivity(1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), 50);
        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(notStarted));

        var list = seckillService.listActivities(1);
        assertTrue(list.isEmpty());
    }
}
