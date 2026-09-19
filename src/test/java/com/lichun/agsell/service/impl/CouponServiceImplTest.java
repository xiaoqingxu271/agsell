package com.lichun.agsell.service.impl;

import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CouponMapper;
import com.lichun.agsell.mapper.CouponProductMapper;
import com.lichun.agsell.mapper.UserCouponMapper;
import com.lichun.agsell.model.dto.CouponCreateRequest;
import com.lichun.agsell.model.entity.Coupon;
import com.lichun.agsell.model.entity.UserCoupon;
import com.lichun.agsell.model.vo.CouponVerifyVO;
import com.lichun.agsell.model.vo.UserCouponVO;
import com.lichun.agsell.service.CouponRedisService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 优惠券模块单元测试：领取/防超发/防重、核销/门槛/双花、回退幂等、创建校验
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponProductMapper couponProductMapper;
    @Mock
    private CouponRedisService couponRedisService;

    @InjectMocks
    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        // 纯 Mockito 环境无 Spring 上下文：手动注册实体元数据，LambdaUpdateWrapper 才能解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserCoupon.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Coupon.class);
        BaseContext.setCurrentId(1L, "test-jti");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    /** 限量满减券：满 99 减 10，总量 50，7 天有效 */
    private Coupon limitedCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(101L);
        coupon.setCouponName("满99减10");
        coupon.setCouponType(1);
        coupon.setThreshold(new BigDecimal("99.00"));
        coupon.setAmount(new BigDecimal("10.00"));
        coupon.setTotalCount(50);
        coupon.setReceivedCount(0);
        coupon.setPerUserLimit(1);
        coupon.setValidDays(7);
        coupon.setStatus(1);
        return coupon;
    }

    /** 无门槛 3 元券（不限量） */
    private Coupon unlimitedCoupon() {
        Coupon coupon = limitedCoupon();
        coupon.setId(102L);
        coupon.setCouponName("新人无门槛券");
        coupon.setThreshold(BigDecimal.ZERO);
        coupon.setAmount(new BigDecimal("3.00"));
        coupon.setTotalCount(0);
        return coupon;
    }

    private UserCoupon mockUserCoupon(long id, int status) {
        UserCoupon uc = new UserCoupon();
        uc.setId(id);
        uc.setUserId(1L);
        uc.setCouponId(101L);
        uc.setStatus(status);
        uc.setExpireTime(LocalDateTime.now().plusDays(7));
        return uc;
    }

    // ==================== 领取 ====================

    @Test
    @DisplayName("领取成功：Lua 原子预扣 + 生成 user_coupon + 计数+1")
    void receive_success() {
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(couponRedisService.tryReceive(101L, 1L, true)).thenReturn(1);
        doAnswer(inv -> {
            inv.getArgument(0, UserCoupon.class).setId(999L);
            return 1;
        }).when(userCouponMapper).insert(ArgumentMatchers.<UserCoupon>any());

        UserCouponVO vo = couponService.receiveCoupon(101L);

        assertNotNull(vo);
        assertEquals(999L, vo.getId());
        assertEquals(0, vo.getStatus());
        assertEquals(new BigDecimal("10.00"), vo.getAmount());
        // expire_time = 领取时间 + 7 天
        assertNotNull(vo.getExpireTime());
        verify(couponMapper).update(any(), any()); // received_count + 1
    }

    @Test
    @DisplayName("重复领取（Redis 标记）：COUPON_RECEIVE_REPEAT")
    void receive_repeat() {
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(couponRedisService.tryReceive(101L, 1L, true)).thenReturn(-2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_RECEIVE_REPEAT.getCode(), ex.getCode());
        verify(userCouponMapper, never()).insert(ArgumentMatchers.<UserCoupon>any());
    }

    @Test
    @DisplayName("已领完（Lua 返回 0）：COUPON_SOLD_OUT")
    void receive_soldOut() {
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(couponRedisService.tryReceive(101L, 1L, true)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_SOLD_OUT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已下架：COUPON_NOT_FOUND")
    void receive_disabled() {
        Coupon coupon = limitedCoupon();
        coupon.setStatus(0);
        when(couponMapper.selectById(101L)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("未开始领取：COUPON_RECEIVE_END")
    void receive_notStarted() {
        Coupon coupon = limitedCoupon();
        coupon.setStartTime(LocalDateTime.now().plusHours(1));
        when(couponMapper.selectById(101L)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_RECEIVE_END.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已停止领取：COUPON_RECEIVE_END")
    void receive_ended() {
        Coupon coupon = limitedCoupon();
        coupon.setEndTime(LocalDateTime.now().minusHours(1));
        when(couponMapper.selectById(101L)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_RECEIVE_END.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("DB 唯一索引冲突（并发穿透 Redis）：COUPON_RECEIVE_REPEAT + Redis 补偿")
    void receive_dbUniqueKeyConflict() {
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(couponRedisService.tryReceive(101L, 1L, true)).thenReturn(1);
        doThrow(new DuplicateKeyException("uk_user_coupon"))
                .when(userCouponMapper).insert(ArgumentMatchers.<UserCoupon>any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.receiveCoupon(101L));
        assertEquals(ErrorCode.COUPON_RECEIVE_REPEAT.getCode(), ex.getCode());
        verify(couponRedisService).releaseReceive(101L, 1L, true);
    }

    @Test
    @DisplayName("DB 插入异常：补偿 Redis 后异常传播")
    void receive_dbErrorCompensate() {
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(couponRedisService.tryReceive(101L, 1L, true)).thenReturn(1);
        doThrow(new RuntimeException("db down")).when(userCouponMapper).insert(ArgumentMatchers.<UserCoupon>any());

        assertThrows(RuntimeException.class, () -> couponService.receiveCoupon(101L));
        verify(couponRedisService).releaseReceive(101L, 1L, true);
    }

    @Test
    @DisplayName("无限量券领取：跳过库存段（limited=false）")
    void receive_unlimited() {
        when(couponMapper.selectById(102L)).thenReturn(unlimitedCoupon());
        when(couponRedisService.tryReceive(102L, 1L, false)).thenReturn(1);
        doAnswer(inv -> {
            inv.getArgument(0, UserCoupon.class).setId(888L);
            return 1;
        }).when(userCouponMapper).insert(ArgumentMatchers.<UserCoupon>any());

        UserCouponVO vo = couponService.receiveCoupon(102L);
        assertNotNull(vo);
        assertEquals(888L, vo.getId());
        verify(couponRedisService).tryReceive(102L, 1L, false);
    }

    // ==================== 核销 ====================

    @Test
    @DisplayName("核销成功：status 0→1 + 回填订单号 + discount=min(面额,订单金额)")
    void verify_success() {
        when(userCouponMapper.selectById(999L)).thenReturn(mockUserCoupon(999L, 0));
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(userCouponMapper.update(any(), any())).thenReturn(1);

        CouponVerifyVO vo = couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, false, "AGS123");

        assertEquals(new BigDecimal("10.00"), vo.getDiscount());
        assertEquals("满99减10", vo.getCouponName());
        assertEquals(999L, vo.getUserCouponId());
    }

    @Test
    @DisplayName("面额超过订单金额：discount 取订单金额（兜底）")
    void verify_discountCappedByTotal() {
        // 无门槛 3 元券，订单金额 2 元 → 优惠按 2 元
        when(userCouponMapper.selectById(999L)).thenReturn(mockUserCoupon(999L, 0));
        when(couponMapper.selectById(101L)).thenReturn(unlimitedCoupon());
        when(userCouponMapper.update(any(), any())).thenReturn(1);

        CouponVerifyVO vo = couponService.verifyCoupon(999L, 1L, new BigDecimal("2.00"), null, false, "AGS124");
        assertEquals(new BigDecimal("2.00"), vo.getDiscount()); // min(3.00, 2.00)
        assertEquals("新人无门槛券", vo.getCouponName());
    }

    @Test
    @DisplayName("未选券：返回优惠金额 0，不查库")
    void verify_noCoupon() {
        CouponVerifyVO vo = couponService.verifyCoupon(null, 1L, new BigDecimal("50.00"), null, false, "AGS125");
        assertEquals(BigDecimal.ZERO, vo.getDiscount());
        assertNull(vo.getCouponName());
        verify(userCouponMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("门槛不足：COUPON_THRESHOLD_NOT_MET")
    void verify_thresholdNotMet() {
        when(userCouponMapper.selectById(999L)).thenReturn(mockUserCoupon(999L, 0));
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("50.00"), null, false, "AGS126"));
        assertEquals(ErrorCode.COUPON_THRESHOLD_NOT_MET.getCode(), ex.getCode());
        verify(userCouponMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("券已过期：COUPON_INVALID")
    void verify_expired() {
        UserCoupon uc = mockUserCoupon(999L, 0);
        uc.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(userCouponMapper.selectById(999L)).thenReturn(uc);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, false, "AGS127"));
        assertEquals(ErrorCode.COUPON_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("他人券：COUPON_INVALID")
    void verify_notOwner() {
        UserCoupon uc = mockUserCoupon(999L, 0);
        uc.setUserId(2L);
        when(userCouponMapper.selectById(999L)).thenReturn(uc);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, false, "AGS128"));
        assertEquals(ErrorCode.COUPON_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("券已被使用：COUPON_INVALID")
    void verify_alreadyUsed() {
        when(userCouponMapper.selectById(999L)).thenReturn(mockUserCoupon(999L, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, false, "AGS129"));
        assertEquals(ErrorCode.COUPON_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("并发双花：条件 UPDATE 影响行数为 0 → COUPON_INVALID")
    void verify_concurrentDoubleSpend() {
        when(userCouponMapper.selectById(999L)).thenReturn(mockUserCoupon(999L, 0));
        when(couponMapper.selectById(101L)).thenReturn(limitedCoupon());
        when(userCouponMapper.update(any(), any())).thenReturn(0); // 另一单已抢先核销

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, false, "AGS130"));
        assertEquals(ErrorCode.COUPON_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("秒杀订单禁用券：COUPON_SECKILL_FORBIDDEN")
    void verify_seckillForbidden() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.verifyCoupon(999L, 1L, new BigDecimal("128.00"), null, true, "AGS131"));
        assertEquals(ErrorCode.COUPON_SECKILL_FORBIDDEN.getCode(), ex.getCode());
    }

    // ==================== 回退 ====================

    @Test
    @DisplayName("回退：status 1→0 + 清空订单号；无券 ID 时不执行")
    void refund_idempotent() {
        couponService.refundCoupon(999L, "AGS132");
        verify(userCouponMapper).update(any(), any());

        couponService.refundCoupon(null, "AGS133");
        verify(userCouponMapper, times(1)).update(any(), any()); // 未新增调用
    }

    // ==================== 创建校验 ====================

    @Test
    @DisplayName("创建券：非法参数被拦截（面额<=0 / 门槛<0 / 总量<0 / 每人限领非1 / 有效期<=0）")
    void create_validation() {
        CouponCreateRequest request = new CouponCreateRequest();
        request.setCouponName("测试券");
        request.setCouponType(1);
        request.setThreshold(BigDecimal.ZERO);
        request.setAmount(BigDecimal.ZERO); // 非法
        request.setTotalCount(100);
        request.setPerUserLimit(1);
        request.setValidDays(7);
        assertThrows(BusinessException.class, () -> couponService.createCoupon(request));

        request.setAmount(new BigDecimal("5.00"));
        request.setThreshold(new BigDecimal("-1"));
        assertThrows(BusinessException.class, () -> couponService.createCoupon(request));

        request.setThreshold(BigDecimal.ZERO);
        request.setPerUserLimit(2);
        assertThrows(BusinessException.class, () -> couponService.createCoupon(request));

        request.setPerUserLimit(1);
        request.setValidDays(0);
        assertThrows(BusinessException.class, () -> couponService.createCoupon(request));
        verify(couponMapper, never()).insert(ArgumentMatchers.<Coupon>any());
    }

    @Test
    @DisplayName("创建成功：入库 + 同步 Redis 剩余量")
    void create_success() {
        CouponCreateRequest request = new CouponCreateRequest();
        request.setCouponName("满99减10");
        request.setCouponType(1);
        request.setThreshold(new BigDecimal("99.00"));
        request.setAmount(new BigDecimal("10.00"));
        request.setTotalCount(50);
        request.setPerUserLimit(1);
        request.setValidDays(7);
        request.setSort(10);
        request.setStatus(1);
        doAnswer(inv -> {
            inv.getArgument(0, Coupon.class).setId(101L);
            return 1;
        }).when(couponMapper).insert(ArgumentMatchers.<Coupon>any());

        Long id = couponService.createCoupon(request);
        assertEquals(101L, id);
        verify(couponRedisService).syncStock(any(Coupon.class));
    }
}
