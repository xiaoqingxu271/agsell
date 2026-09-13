package com.lichun.agsell.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lichun.agsell.model.entity.SeckillActivity;
import com.lichun.agsell.service.SeckillRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 秒杀 Redis 服务实现
 * Key 设计：
 *   seckill:activity:{id}    活动快照（JSON）
 *   seckill:stock:{id}       剩余秒杀库存（int）
 *   seckill:user:{id}:{userId} 用户已抢标记（1）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillRedisServiceImpl implements SeckillRedisService {

    private final StringRedisTemplate redisTemplate;

    /** 快照序列化器（自建实例，避免依赖容器 ObjectMapper 自动配置） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String ACTIVITY_KEY = "seckill:activity:";
    private static final String STOCK_KEY = "seckill:stock:";
    private static final String USER_KEY = "seckill:user:";

    /**
     * 抢购原子脚本：查库存 → 扣库存 → 查重 → 标记用户，单次网络往返完成，无竞态窗口。
     * 返回：1=成功  0=已售罄  -1=活动未初始化/不存在  -2=已参与过
     */
    private static final DefaultRedisScript<Long> TRY_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -1 end
            if stock <= 0 then return 0 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], '1')
            return 1
            """, Long.class);

    /**
     * 释放原子脚本：仅当用户标记存在时回补库存并清除标记（幂等，防重复释放导致库存虚高）。
     * 返回：1=已释放  0=无标记（无需释放）
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('INCR', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end
            return 0
            """, Long.class);

    @Override
    public SeckillActivity getSnapshot(Long activityId) {
        String json = redisTemplate.opsForValue().get(ACTIVITY_KEY + activityId);
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, SeckillActivity.class);
        } catch (Exception e) {
            log.warn("[SeckillRedis] 活动快照反序列化失败, activityId={}", activityId, e);
            return null;
        }
    }

    @Override
    public void saveSnapshot(SeckillActivity activity) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(activity);
            redisTemplate.opsForValue().set(ACTIVITY_KEY + activity.getId(), json);
            // 重置剩余库存为活动总库存（编辑活动时同步回写）
            redisTemplate.opsForValue().set(STOCK_KEY + activity.getId(), String.valueOf(activity.getSeckillStock()));
            log.info("[SeckillRedis] 写入活动快照, activityId={}, stock={}", activity.getId(), activity.getSeckillStock());
        } catch (Exception e) {
            log.error("[SeckillRedis] 活动快照序列化失败, activityId={}", activity.getId(), e);
        }
    }

    @Override
    public void updateStatusSnapshot(Long activityId, int status) {
        SeckillActivity snapshot = getSnapshot(activityId);
        if (snapshot == null) {
            return;
        }
        snapshot.setStatus(status);
        saveSnapshot(snapshot);
    }

    @Override
    public void deleteSnapshot(Long activityId) {
        redisTemplate.delete(ACTIVITY_KEY + activityId);
        redisTemplate.delete(STOCK_KEY + activityId);
        // 清理该活动全部用户标记（pattern 删除）
        java.util.Set<String> userKeys = redisTemplate.keys(USER_KEY + activityId + ":*");
        if (userKeys != null && !userKeys.isEmpty()) {
            redisTemplate.delete(userKeys);
        }
        log.info("[SeckillRedis] 删除活动缓存, activityId={}", activityId);
    }

    @Override
    public int trySeckill(Long activityId, Long userId) {
        Long result = redisTemplate.execute(TRY_SCRIPT,
                java.util.List.of(STOCK_KEY + activityId, USER_KEY + activityId + ":" + userId),
                String.valueOf(userId));
        return result == null ? -1 : result.intValue();
    }

    @Override
    public Integer getRemainingStock(Long activityId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY + activityId);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("[SeckillRedis] 剩余库存解析失败, activityId={}, value={}", activityId, value);
            return null;
        }
    }

    @Override
    public void releaseSeckill(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            return;
        }
        Long result = redisTemplate.execute(RELEASE_SCRIPT,
                java.util.List.of(STOCK_KEY + activityId, USER_KEY + activityId + ":" + userId));
        if (result != null && result == 1L) {
            log.info("[SeckillRedis] 释放秒杀名额, activityId={}, userId={}", activityId, userId);
        }
    }

    @Override
    public boolean hasUserSeckilled(Long activityId, Long userId) {
        if (userId == null) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(USER_KEY + activityId + ":" + userId);
        return Boolean.TRUE.equals(exists);
    }
}
