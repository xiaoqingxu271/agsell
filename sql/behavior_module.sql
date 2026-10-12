-- ============================================================
-- 用户行为模块数据库变更（浏览足迹，供 AI 个性化推荐的实时行为信号）
-- 兼容 MySQL 5.7 / 8.x；可重复执行（幂等）
-- ============================================================

USE `agsell`;

-- 1. 用户商品浏览足迹表
-- 粒度为「用户 × 商品 × 自然日」：同日重复浏览只累加 view_count，
-- 偏好聚合按 (product_id, view_date) 去重计权，避免反复刷同一商品自强化。
-- category_id 冗余存储，使偏好 SQL 聚合无需再 join product。
CREATE TABLE IF NOT EXISTS `user_product_view` (
    `id`             BIGINT   NOT NULL COMMENT '主键（雪花ID，应用层生成）',
    `user_id`        BIGINT   NOT NULL COMMENT '用户ID',
    `product_id`     BIGINT   NOT NULL COMMENT '商品ID',
    `category_id`    BIGINT   NOT NULL COMMENT '类目ID（冗余，偏好聚合免 join product）',
    `view_date`      DATE     NOT NULL COMMENT '浏览自然日（同日合并为一条）',
    `view_count`     INT      NOT NULL DEFAULT 1 COMMENT '当日浏览次数（仅统计，不参与偏好权重）',
    `last_view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当日最后一次浏览时间',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product_date` (`user_id`, `product_id`, `view_date`),
    KEY `idx_user_date` (`user_id`, `view_date`),
    KEY `idx_user_category` (`user_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户商品浏览足迹（个性化行为信号）';
