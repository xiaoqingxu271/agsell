-- =============================================================
-- 优惠券模块 v1.1：券类型扩展（折扣券 + 品类券）
-- 1) coupon 表新增折扣率 / 封顶金额两列
-- 2) 新增品类券适用商品关联表 coupon_product
-- 幂等：可重复执行（列存在则忽略）
-- =============================================================

USE `agsell`;

-- 1. coupon 表加列（折扣券用）
--    注：amount 一期为 NOT NULL，折扣券不填 amount，需改为可空
ALTER TABLE `coupon`
    MODIFY COLUMN `amount` DECIMAL(10,2) NULL COMMENT '优惠金额（满减/品类券面额，折扣券为空）',
    ADD COLUMN  `discount`     DECIMAL(3,2)  DEFAULT NULL COMMENT '折扣率（仅折扣券，0.85=85折，0~1开区间）' AFTER `amount`,
    ADD COLUMN  `max_discount` DECIMAL(10,2) DEFAULT NULL COMMENT '折扣封顶金额（仅折扣券，0/NULL=不封顶）' AFTER `discount`;

-- 2. 品类券商品关联表
DROP TABLE IF EXISTS `coupon_product`;
CREATE TABLE `coupon_product` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `coupon_id`   BIGINT      NOT NULL COMMENT '券模板ID',
    `product_id`  BIGINT      NOT NULL COMMENT '商品ID',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_product` (`coupon_id`, `product_id`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品类券适用商品关联表';
