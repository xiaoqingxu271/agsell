package com.lichun.agsell.service;

import com.lichun.agsell.model.entity.Coupon;

/**
 * 优惠券 Redis 服务：剩余量缓存 + Lua 原子领取 + 失败补偿
 */
public interface CouponRedisService {

    /**
     * Lua 原子领取（防超发 + 防重复领取）
     *
     * @param limited 是否限量券（total_count &gt; 0）。限量券检查剩余量并 DECR，无限量券仅查重标记
     * @return 1=成功 0=已领完 -1=未初始化/不存在 -2=已领取过
     */
    int tryReceive(Long couponId, Long userId, boolean limited);

    /**
     * 领取失败补偿（幂等）：回补剩余量并清除用户标记
     */
    void releaseReceive(Long couponId, Long userId, boolean limited);

    /**
     * 当前用户是否已领取过该券
     */
    boolean hasUserReceived(Long couponId, Long userId);

    /**
     * 同步剩余量缓存（创建/编辑/上下架时调用，重置为已领取数口径的剩余量）
     */
    void syncStock(Coupon coupon);

    /**
     * 删除券相关全部缓存（删除券时调用）
     */
    void deleteCache(Long couponId);
}
