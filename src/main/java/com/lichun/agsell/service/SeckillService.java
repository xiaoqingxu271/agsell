package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.SeckillActivityRequest;
import com.lichun.agsell.model.dto.SeckillOrderRequest;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.SeckillActivityVO;
import com.lichun.agsell.model.vo.SeckillDetailVO;
import com.lichun.agsell.model.vo.SeckillListItemVO;

import java.util.List;

/**
 * 秒杀服务：活动管理（管理端）+ 抢购/释放（用户端）
 */
public interface SeckillService {

    // ==================== 用户端 ====================

    /**
     * 秒杀活动列表
     *
     * @param filter 1=进行中 0=未开始+进行中 null=全部（不含已下架）
     */
    List<SeckillListItemVO> listActivities(Integer filter);

    /**
     * 秒杀活动详情（含当前用户是否已抢）
     *
     * @param activityCode 活动编号（对外随机标识）
     */
    SeckillDetailVO getActivityDetail(String activityCode);

    /**
     * 秒杀下单：Redis Lua 原子预扣 + 创建待付款订单
     */
    OrderCreateVO createSeckillOrder(SeckillOrderRequest request);

    /**
     * 释放秒杀名额（订单取消/超时取消时调用，幂等）
     */
    void releaseSeckillQuota(Long activityId, Long userId);

    // ==================== 管理端 ====================

    /**
     * 秒杀活动分页（商品名模糊 + 状态筛选）
     */
    Page<SeckillActivityVO> pageActivities(int pageNum, int pageSize, String keyword, Integer status);

    /**
     * 创建秒杀活动（校验 + 写 Redis 快照）
     */
    Long createActivity(SeckillActivityRequest request);

    /**
     * 编辑秒杀活动（已开始的活动中止编辑库存/时间）
     */
    void updateActivity(Long id, SeckillActivityRequest request);

    /**
     * 删除秒杀活动（软删 + 清 Redis）
     */
    void deleteActivity(Long id);

    /**
     * 秒杀活动上下架
     */
    void updateActivityStatus(Long id, Integer status);
}
