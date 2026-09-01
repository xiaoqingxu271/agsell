-- ============================================================
-- 购物车 + 订单模块建表脚本
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 购物车表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
    `spec_id`     BIGINT       DEFAULT NULL COMMENT '规格ID',
    `quantity`    INT          NOT NULL DEFAULT 1 COMMENT '购买数量',
    `selected`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否选中 0=否 1=是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_product_id` (`product_id`),
    UNIQUE KEY `uk_user_product_spec` (`user_id`, `product_id`, `spec_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ------------------------------------------------------------
-- 订单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(32)    NOT NULL COMMENT '订单号',
    `user_id`       BIGINT         NOT NULL COMMENT '用户ID',
    `total_amount`  DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '商品总金额',
    `freight`       DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '运费',
    `discount`      DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `pay_amount`    DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '状态 0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=售后中',
    `address_id`    BIGINT         NOT NULL COMMENT '收货地址ID',
    `receiver`      VARCHAR(50)    NOT NULL COMMENT '收件人',
    `phone`         VARCHAR(20)    NOT NULL COMMENT '收件电话',
    `address`       VARCHAR(255)   NOT NULL COMMENT '详细地址',
    `pay_type`      TINYINT        DEFAULT NULL COMMENT '支付方式 1=模拟支付',
    `pay_time`      DATETIME       DEFAULT NULL COMMENT '支付时间',
    `delivery_time` DATETIME       DEFAULT NULL COMMENT '发货时间',
    `receive_time`  DATETIME       DEFAULT NULL COMMENT '确认收货时间',
    `log_type`      VARCHAR(50)    DEFAULT NULL COMMENT '物流公司',
    `log_no`        VARCHAR(50)    DEFAULT NULL COMMENT '物流单号',
    `remark`        VARCHAR(255)   DEFAULT NULL COMMENT '买家备注',
    `cancel_reason` VARCHAR(255)   DEFAULT NULL COMMENT '取消原因',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT        NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ------------------------------------------------------------
-- 订单明细表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT         NOT NULL COMMENT '订单ID',
    `product_id`     BIGINT         NOT NULL COMMENT '商品ID',
    `product_name`   VARCHAR(100)   NOT NULL COMMENT '商品名称（快照）',
    `product_image`  VARCHAR(255)   DEFAULT NULL COMMENT '商品图片（快照）',
    `spec_name`      VARCHAR(50)    DEFAULT NULL COMMENT '规格名称（快照）',
    `price`          DECIMAL(10,2)  NOT NULL COMMENT '单价（快照）',
    `quantity`       INT            NOT NULL DEFAULT 1 COMMENT '数量',
    `subtotal`       DECIMAL(10,2)  NOT NULL COMMENT '小计',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- ------------------------------------------------------------
-- 测试数据：购物车
-- ------------------------------------------------------------
INSERT INTO `cart` (`user_id`, `product_id`, `spec_id`, `quantity`, `selected`) VALUES
(6, 1, 2, 2, 1),
(6, 2, 4, 1, 1),
(6, 3, 6, 1, 0);

-- ------------------------------------------------------------
-- 测试数据：订单
-- ------------------------------------------------------------
INSERT INTO `order` (`order_no`, `user_id`, `total_amount`, `freight`, `discount`, `pay_amount`, `status`, `address_id`, `receiver`, `phone`, `address`, `pay_type`, `pay_time`, `remark`) VALUES
('AGS20260826000000001', 6, 79.80, 0.00, 0.00, 79.80, 3, 1, 'John', '13900139001', '江西省赣州市章贡区xxx路xxx号', 1, '2026-08-25 10:00:00', '请尽快发货'),
('AGS20260826000000002', 6, 12.90, 0.00, 0.00, 12.90, 1, 1, 'John', '13900139001', '江西省赣州市章贡区xxx路xxx号', 1, '2026-08-25 12:00:00', NULL);

-- ------------------------------------------------------------
-- 测试数据：订单明细
-- ------------------------------------------------------------
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `spec_name`, `price`, `quantity`, `subtotal`) VALUES
(1, 1, '赣南脐橙 新鲜当季', NULL, '1kg 装', 39.90, 2, 79.80),
(2, 2, '有机西兰花 新鲜蔬菜', NULL, '500g 装', 12.90, 1, 12.90);
