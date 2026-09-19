package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.CouponCreateRequest;
import com.lichun.agsell.model.vo.CouponAvailableVO;
import com.lichun.agsell.model.vo.CouponListItemVO;
import com.lichun.agsell.model.vo.CouponVO;
import com.lichun.agsell.model.vo.CouponVerifyVO;
import com.lichun.agsell.model.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券服务：领取/核销/回退（用户端）+ 券模板管理（管理端）+ 过期批处理
 */
public interface CouponService {

    // ==================== 用户端 ====================

    /**
     * 领券中心：可领券列表（上架中 + 领取时间内 + 未领完），含当前用户已领标记
     */
    List<CouponListItemVO> listCoupons();

    /**
     * 领取优惠券（Redis Lua 原子防超发/防重 + DB 唯一索引兜底）
     */
    UserCouponVO receiveCoupon(Long couponId);

    /**
     * 我的券包（分页；实时过期修正：已过期券按 status=2 展示）
     */
    Page<UserCouponVO> myCoupons(int pageNum, int pageSize, Integer status);

    /**
     * 结算页可用券（按门槛 + 品类券商品范围过滤，usable/unusable 两组）
     *
     * @param totalAmount 订单商品总金额
     * @param productIds  订单商品ID集合（品类券用，普通券可空）
     */
    CouponAvailableVO listAvailable(BigDecimal totalAmount, java.util.List<Long> productIds);

    /**
     * 下单核销（供 OrderServiceImpl 调用）：校验归属/门槛/商品范围/有效期/秒杀互斥后原子核销
     *
     * @param userCouponId 用户券ID（null 表示不用券）
     * @param userId       当前用户
     * @param totalAmount  订单商品总金额
     * @param productIds   订单商品ID集合（品类券校验用，可空）
     * @param seckill      是否秒杀订单（秒杀单禁用券）
     * @param orderNo      核销订单号（回填 user_coupon，对账用）
     * @return 核销结果（含优惠金额与券名称快照；未用券返回金额 0、名称为 null）
     */
    CouponVerifyVO verifyCoupon(Long userCouponId, Long userId, BigDecimal totalAmount,
                                java.util.List<Long> productIds, boolean seckill, String orderNo);

    /**
     * 订单取消/超时回退券（供 OrderServiceImpl 调用，幂等：仅匹配原核销订单号）
     */
    void refundCoupon(Long userCouponId, String orderNo);

    // ==================== 管理端 ====================

    /**
     * 券模板分页（名称模糊 + 状态筛选）
     */
    Page<CouponVO> pageCoupons(int pageNum, int pageSize, String keyword, Integer status);

    /**
     * 创建券模板（校验规则 + 同步 Redis）
     */
    Long createCoupon(CouponCreateRequest request);

    /**
     * 编辑券模板（上架中禁止改面额/门槛/总量/有效期；同步 Redis）
     */
    void updateCoupon(Long id, CouponCreateRequest request);

    /**
     * 删除券模板（软删 + 清 Redis；已领取券不可再核销）
     */
    void deleteCoupon(Long id);

    /**
     * 券模板上下架（同步 Redis 剩余量）
     */
    void updateCouponStatus(Long id, Integer status);

    // ==================== 内部 ====================

    /**
     * 过期批处理：status=0 且 expire_time < now 的券批量置为已过期（每日定时任务调用）
     *
     * @return 置为过期的数量
     */
    int expireCoupons();
}
