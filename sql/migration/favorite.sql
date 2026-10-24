-- ============================================================
-- 收藏模块（增量脚本）
-- 背景：收藏此前整个不存在（评估清单 2.1）。收藏是比浏览可信、比下单稠密的
-- 强意向信号：落库后既支撑用户端「我的收藏」，也作为 AI 个性化偏好的一路信号
-- （preference_repo.favorite_category_weights，与订单/好评/加购/浏览/搜索词并列）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 取消收藏即删行（无软删列）：收藏列表不保留"曾收藏过"的痕迹。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `favorite` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `product_id`  BIGINT   NOT NULL COMMENT '商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏';
