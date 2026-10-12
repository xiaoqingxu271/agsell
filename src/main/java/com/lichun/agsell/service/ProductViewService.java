package com.lichun.agsell.service;

/**
 * 用户商品浏览足迹服务（AI 个性化推荐的实时行为信号）
 */
public interface ProductViewService {

    /**
     * 记录一次商品浏览（best-effort，永不抛异常）。
     * <p>
     * 游客（userId 为 null）、商品不存在或已下架时静默跳过；埋点失败只记日志，
     * 绝不把错误抛给前端——看商品是用户的主流程，记账问题不该打扰用户。
     *
     * @param userId    登录用户ID，可为 null
     * @param productId 商品ID
     */
    void recordView(Long userId, Long productId);
}
