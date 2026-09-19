-- ============================================================
-- 优惠券模块建表脚本
-- 1. 新增 coupon 券模板表
-- 2. 新增 user_coupon 用户券表
-- 3. order 表追加 coupon_id / coupon_name 字段 + 索引
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 券模板表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `coupon_name`    VARCHAR(50)   NOT NULL COMMENT '券名称（如：满99减10）',
    `coupon_type`    TINYINT       NOT NULL DEFAULT 1 COMMENT '券类型 1=满减券（本期仅此类型，预留2=折扣券）',
    `threshold`      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（订单满X元可用，0=无门槛）',
    `amount`         DECIMAL(10,2) NOT NULL COMMENT '优惠金额（满减面额）',
    `total_count`    INT           NOT NULL DEFAULT 0 COMMENT '发放总量（0=不限量）',
    `received_count` INT           NOT NULL DEFAULT 0 COMMENT '已领取数量（冗余计数，Redis 预热数据源）',
    `per_user_limit` INT           NOT NULL DEFAULT 1 COMMENT '每人限领数量（本期固定1）',
    `valid_days`     INT           NOT NULL DEFAULT 7 COMMENT '领取后有效天数（相对有效期，从领取时刻起算）',
    `start_time`     DATETIME      DEFAULT NULL COMMENT '领取开始时间（NULL=立即开始）',
    `end_time`       DATETIME      DEFAULT NULL COMMENT '领取结束时间（NULL=长期有效）',
    `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 0=下架 1=上架',
    `sort`           INT           NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- ------------------------------------------------------------
-- 用户券表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT        NOT NULL COMMENT '用户ID',
    `coupon_id`    BIGINT        NOT NULL COMMENT '券模板ID',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0=未使用 1=已使用 2=已过期',
    `order_no`     VARCHAR(32)   DEFAULT NULL COMMENT '核销订单号（已使用时记录，对账用）',
    `use_time`     DATETIME      DEFAULT NULL COMMENT '核销时间',
    `expire_time`  DATETIME      NOT NULL COMMENT '该张券的过期时间（= 领取时间 + valid_days）',
    `receive_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- ------------------------------------------------------------
-- 订单表扩展：优惠券标识 + 快照名称
-- MySQL 普通索引对 NULL 不参与冲突校验，普通订单（coupon_id=NULL）零影响
-- ------------------------------------------------------------
ALTER TABLE `order`
    ADD COLUMN `coupon_id` BIGINT DEFAULT NULL COMMENT '用户优惠券ID（user_coupon.id，非优惠订单为NULL）' AFTER `remark`,
    ADD COLUMN `coupon_name` VARCHAR(50) DEFAULT NULL COMMENT '优惠券名称（快照，券模板删除后仍可展示）' AFTER `coupon_id`,
    ADD KEY `idx_coupon_id` (`coupon_id`);

-- ------------------------------------------------------------
-- 测试数据：券模板（2 张：无门槛 3 元券 + 满 99 减 10 券）
-- ------------------------------------------------------------
INSERT INTO `coupon` (`coupon_name`, `coupon_type`, `threshold`, `amount`, `total_count`, `received_count`, `per_user_limit`, `valid_days`, `status`, `sort`) VALUES
('新人无门槛券', 1, 0.00, 3.00, 1000, 0, 1, 7, 1, 100),
('满99减10', 1, 99.00, 10.00, 500, 0, 1, 30, 1, 90);
