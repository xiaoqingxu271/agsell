-- ============================================================
-- 秒杀模块建表脚本
-- 1. 新增 seckill_activity 秒杀活动表
-- 2. order 表追加 seckill_activity_id 字段 + 一人一单唯一索引
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 秒杀活动表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `seckill_activity`;
CREATE TABLE `seckill_activity` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（内部使用，不对外暴露）',
    `activity_code`    VARCHAR(32)   NOT NULL COMMENT '活动编号（对外暴露的随机标识，不可枚举）',
    `product_id`       BIGINT        NOT NULL COMMENT '商品ID',
    `product_spec_id`  BIGINT        DEFAULT NULL COMMENT '绑定规格ID（无规格为NULL，按商品维度秒杀）',
    `seckill_price`    DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `seckill_stock`    INT           NOT NULL DEFAULT 0 COMMENT '秒杀库存（活动放出的名额）',
    `seckill_limit`    INT           NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time`       DATETIME      NOT NULL COMMENT '开始时间',
    `end_time`         DATETIME      NOT NULL COMMENT '结束时间',
    `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 0=下架 1=上架',
    `sort`             INT           NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_code` (`activity_code`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动表';

-- 存量库升级（已存在表时执行）：新增活动编号字段并回填存量数据
-- ALTER TABLE `seckill_activity`
--     ADD COLUMN `activity_code` VARCHAR(32) NOT NULL COMMENT '活动编号（对外暴露的随机标识，不可枚举）' AFTER `id`,
--     ADD UNIQUE KEY `uk_activity_code` (`activity_code`);
-- UPDATE `seckill_activity` SET `activity_code` = UPPER(SUBSTRING(MD5(RAND()), 1, 16)) WHERE `activity_code` = '';

-- ------------------------------------------------------------
-- 订单表扩展：秒杀活动标识 + 一人一单唯一索引
-- MySQL 唯一索引对 NULL 不参与冲突校验，普通订单（seckill_activity_id=NULL）零影响
-- ------------------------------------------------------------
ALTER TABLE `order`
    ADD COLUMN `seckill_activity_id` BIGINT DEFAULT NULL COMMENT '秒杀活动ID（非秒杀订单为NULL）' AFTER `remark`,
    ADD UNIQUE KEY `uk_user_seckill` (`user_id`, `seckill_activity_id`);
