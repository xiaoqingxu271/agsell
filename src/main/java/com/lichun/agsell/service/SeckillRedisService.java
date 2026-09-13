package com.lichun.agsell.service;

import com.lichun.agsell.model.entity.SeckillActivity;

/**
 * 秒杀 Redis 服务：活动快照缓存 + Lua 原子抢购 + 名额释放
 */
public interface SeckillRedisService {

    /**
     * 读活动快照（Redis），无缓存返回 null
     */
    SeckillActivity getSnapshot(Long activityId);

    /**
     * 写活动快照并重置秒杀库存（创建/编辑活动时调用）
     */
    void saveSnapshot(SeckillActivity activity);

    /**
     * 更新快照中的活动状态（上下架）
     */
    void updateStatusSnapshot(Long activityId, int status);

    /**
     * 删除活动相关全部缓存（删除活动时调用）
     */
    void deleteSnapshot(Long activityId);

    /**
     * Lua 原子抢购
     *
     * @return 1=成功 0=已售罄 -1=活动未初始化 -2=已参与过
     */
    int trySeckill(Long activityId, Long userId);

    /**
     * 查询剩余秒杀库存；无缓存返回 null
     */
    Integer getRemainingStock(Long activityId);

    /**
     * 幂等释放名额：用户标记存在则回补库存并清除标记
     */
    void releaseSeckill(Long activityId, Long userId);

    /**
     * 当前用户是否已参与过该秒杀
     */
    boolean hasUserSeckilled(Long activityId, Long userId);
}
