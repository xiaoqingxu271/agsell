-- ============================================================
-- 评价 + 轮播图模块建表脚本
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 评价表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT         NOT NULL COMMENT '订单ID',
    `product_id`    BIGINT         NOT NULL COMMENT '商品ID',
    `user_id`       BIGINT         NOT NULL COMMENT '用户ID',
    `rating`        TINYINT        NOT NULL COMMENT '评分 1-5',
    `content`       VARCHAR(500)   DEFAULT NULL COMMENT '评价内容',
    `images`        TEXT COMMENT '评价图片JSON数组',
    `reply_content` VARCHAR(500)   DEFAULT NULL COMMENT '卖家回复',
    `reply_time`    DATETIME       DEFAULT NULL COMMENT '回复时间',
    `is_anonymous`  TINYINT        NOT NULL DEFAULT 0 COMMENT '是否匿名 0=否 1=是',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- ------------------------------------------------------------
-- 轮播图表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(100) DEFAULT NULL COMMENT '标题',
    `image`       VARCHAR(255) NOT NULL COMMENT '图片URL',
    `link`        VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮播图表';

-- ------------------------------------------------------------
-- 测试数据：轮播图
-- ------------------------------------------------------------
INSERT INTO `banner` (`title`, `image`, `link`, `sort`, `status`) VALUES
('当季新鲜水果', 'https://example.com/banner1.jpg', '/product/list?categoryId=1', 100, 1),
('有机蔬菜特惠', 'https://example.com/banner2.jpg', '/product/list?categoryId=2', 90, 1),
('特产农产品', 'https://example.com/banner3.jpg', '/product/list?categoryId=6', 80, 1);

-- ------------------------------------------------------------
-- 测试数据：评价（基于已完成订单）
-- ------------------------------------------------------------
INSERT INTO `review` (`order_id`, `product_id`, `user_id`, `rating`, `content`, `images`, `is_anonymous`, `reply_content`, `reply_time`) VALUES
(1, 1, 6, 5, '非常好吃的脐橙，新鲜多汁！', '["https://example.com/review1.jpg"]', 0, '感谢您的认可，我们会继续努力！', '2026-08-25 12:00:00'),
(1, 2, 6, 4, '蔬菜很新鲜，包装也很好', NULL, 0, NULL, NULL),
(2094607056121999361, 1, 2092484161002274817, 5, '回购多次了，品质稳定', NULL, 1, NULL, NULL);
