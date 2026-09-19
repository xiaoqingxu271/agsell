package com.lichun.agsell.service.impl;

import com.lichun.agsell.model.entity.Coupon;
import com.lichun.agsell.service.CouponRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 优惠券 Redis 服务实现
 * Key 设计：
 *   coupon:stock:{couponId}     剩余可领数量（仅限量券）
 *   coupon:user:{couponId}:{userId} 用户已领标记（1）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRedisServiceImpl implements CouponRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String STOCK_KEY = "coupon:stock:";
    private static final String USER_KEY = "coupon:user:";

    /**
     * 限量券领取原子脚本：查库存 → 扣库存 → 查重 → 标记用户，单次网络往返完成，无竞态窗口。
     * 返回：1=成功  0=已领完  -1=未初始化/不存在  -2=已领取过
     */
    private static final DefaultRedisScript<Long> RECEIVE_LIMITED_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -1 end
            if stock <= 0 then return 0 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], '1')
            return 1
            """, Long.class);

    /**
     * 无限量券领取原子脚本：仅查重 + 标记。
     * 返回：1=成功  -2=已领取过
     */
    private static final DefaultRedisScript<Long> RECEIVE_UNLIMITED_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return -2 end
            redis.call('SET', KEYS[1], '1')
            return 1
            """, Long.class);

    /**
     * 限量券失败补偿脚本：用户标记存在则回补库存并清除标记（幂等）。
     */
    private static final DefaultRedisScript<Long> RELEASE_LIMITED_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('INCR', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end
            return 0
            """, Long.class);

    @Override
    public int tryReceive(Long couponId, Long userId, boolean limited) {
        Long result;
        if (limited) {
            result = redisTemplate.execute(RECEIVE_LIMITED_SCRIPT,
                    List.of(STOCK_KEY + couponId, USER_KEY + couponId + ":" + userId),
                    String.valueOf(userId));
        } else {
            result = redisTemplate.execute(RECEIVE_UNLIMITED_SCRIPT,
                    List.of(USER_KEY + couponId + ":" + userId));
        }
        return result == null ? -1 : result.intValue();
    }

    @Override
    public void releaseReceive(Long couponId, Long userId, boolean limited) {
        if (couponId == null || userId == null) {
            return;
        }
        if (limited) {
            Long result = redisTemplate.execute(RELEASE_LIMITED_SCRIPT,
                    List.of(STOCK_KEY + couponId, USER_KEY + couponId + ":" + userId));
            if (result != null && result == 1L) {
                log.info("[CouponRedis] 补偿回补剩余量, couponId={}, userId={}", couponId, userId);
            }
        } else {
            redisTemplate.delete(USER_KEY + couponId + ":" + userId);
        }
    }

    @Override
    public boolean hasUserReceived(Long couponId, Long userId) {
        if (userId == null) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(USER_KEY + couponId + ":" + userId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void syncStock(Coupon coupon) {
        try {
            if (coupon == null || coupon.getId() == null) {
                return;
            }
            int total = coupon.getTotalCount() == null ? 0 : coupon.getTotalCount();
            int received = coupon.getReceivedCount() == null ? 0 : coupon.getReceivedCount();
            // 限量券：剩余量 = 总量 - 已领取；无限量券（total=0）：不建 stock key
            if (total > 0) {
                int remaining = Math.max(0, total - received);
                redisTemplate.opsForValue().set(STOCK_KEY + coupon.getId(), String.valueOf(remaining));
            } else {
                redisTemplate.delete(STOCK_KEY + coupon.getId());
            }
            log.info("[CouponRedis] 同步剩余量缓存, couponId={}, remaining={}", coupon.getId(), total - received);
        } catch (Exception e) {
            log.error("[CouponRedis] 同步剩余量缓存失败, couponId={}", coupon.getId(), e);
        }
    }

    @Override
    public void deleteCache(Long couponId) {
        redisTemplate.delete(STOCK_KEY + couponId);
        // 清理该券全部用户领取标记（pattern 删除）
        java.util.Set<String> userKeys = redisTemplate.keys(USER_KEY + couponId + ":*");
        if (userKeys != null && !userKeys.isEmpty()) {
            redisTemplate.delete(userKeys);
        }
        log.info("[CouponRedis] 删除优惠券缓存, couponId={}", couponId);
    }
}
