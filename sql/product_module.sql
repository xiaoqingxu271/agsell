-- ============================================================
-- 商品模块建表脚本
-- 创建 product_category、product、product_spec 三张表
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 商品分类表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称',
    `icon`        VARCHAR(255) DEFAULT NULL COMMENT '分类图标URL',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID，0表示一级分类',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ------------------------------------------------------------
-- 商品表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(100)   NOT NULL COMMENT '商品名称',
    `subtitle`       VARCHAR(200)   DEFAULT NULL COMMENT '副标题',
    `category_id`    BIGINT         NOT NULL COMMENT '分类ID',
    `price`          DECIMAL(10,2)  NOT NULL COMMENT '售价',
    `original_price` DECIMAL(10,2)  DEFAULT NULL COMMENT '原价',
    `stock`          INT            NOT NULL DEFAULT 0 COMMENT '库存',
    `sales`          INT            NOT NULL DEFAULT 0 COMMENT '销量',
    `main_image`     VARCHAR(255)   DEFAULT NULL COMMENT '主图URL',
    `images`         TEXT           DEFAULT NULL COMMENT '图片数组JSON',
    `description`    LONGTEXT       DEFAULT NULL COMMENT '商品详情HTML',
    `origin`         VARCHAR(100)   DEFAULT NULL COMMENT '产地',
    `harvest_date`   DATE           DEFAULT NULL COMMENT '采摘/上市日期',
    `shelf_life`     VARCHAR(50)    DEFAULT NULL COMMENT '保质期',
    `storage`        VARCHAR(50)    DEFAULT NULL COMMENT '储存方式',
    `status`         TINYINT        NOT NULL DEFAULT 0 COMMENT '状态 0=下架 1=上架',
    `sort`           INT            NOT NULL DEFAULT 0 COMMENT '排序值',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME       DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT        NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ------------------------------------------------------------
-- 商品规格表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_spec`;
CREATE TABLE `product_spec` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
    `spec_name`   VARCHAR(50)  NOT NULL COMMENT '规格名称（如500g装）',
    `price`       DECIMAL(10,2) NOT NULL COMMENT '该规格价格',
    `stock`       INT          NOT NULL DEFAULT 0 COMMENT '该规格库存',
    `image`       VARCHAR(255) DEFAULT NULL COMMENT '规格图片URL',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品规格表';

-- ------------------------------------------------------------
-- 初始化数据
-- ------------------------------------------------------------

-- 一级分类
INSERT INTO `product_category` (`name`, `parent_id`, `sort`, `status`) VALUES
('水果', 0, 100, 1),
('蔬菜', 0, 90, 1),
('粮油调味', 0, 80, 1),
('肉禽蛋奶', 0, 70, 1),
('干货零食', 0, 60, 1),
('特产农产品', 0, 50, 1);

-- 二级分类
INSERT INTO `product_category` (`name`, `parent_id`, `sort`, `status`) VALUES
(' citrus ', 1, 10, 1),
('浆果类', 1, 9, 1),
('热带水果', 1, 8, 1),
('叶菜类', 2, 10, 1),
('根茎类', 2, 9, 1),
('菌菇类', 2, 8, 1),
('大米杂粮', 3, 10, 1),
('食用油', 3, 9, 1),
('调味品', 3, 8, 1),
('猪肉', 4, 10, 1),
('禽类', 4, 9, 1),
('蛋类', 4, 8, 1),
('坚果炒货', 5, 10, 1),
('蜜饯果干', 5, 9, 1);

-- 示例商品
INSERT INTO `product` (`name`, `subtitle`, `category_id`, `price`, `original_price`, `stock`, `sales`, `main_image`, `status`, `sort`, `origin`, `harvest_date`, `shelf_life`, `storage`) VALUES
('赣南脐橙 新鲜当季', '产地直发 坏果包赔', 7, 39.90, 59.90, 500, 1280, NULL, 1, 100, '江西赣州', '2026-08-20', '30天', '常温避光'),
('有机西兰花 新鲜蔬菜', '无农药残留 基地直供', 13, 12.90, 18.00, 200, 356, NULL, 1, 90, '江西赣州', '2026-08-22', '7天', '冷藏保存'),
('五常大米 东北稻花香', '粒粒晶莹 软糯香甜', 19, 68.00, 88.00, 300, 892, NULL, 1, 80, '黑龙江五常', NULL, '18个月', '阴凉干燥处'),
('土鸡蛋 散养柴鸡蛋', '农家散养 营养丰富', 23, 28.00, 35.00, 150, 567, NULL, 1, 70, '江西赣州', NULL, '15天', '冷藏保存'),
('农家腊肉 烟熏风味', '传统工艺 手工制作', 30, 58.00, 78.00, 80, 234, NULL, 1, 60, '湖南湘西', NULL, '6个月', '冷冻保存');

-- 示例商品规格
INSERT INTO `product_spec` (`product_id`, `spec_name`, `price`, `stock`, `image`) VALUES
(1, '500g 装', 19.90, 200, NULL),
(1, '1kg 装', 39.90, 150, NULL),
(1, '3kg 装', 99.90, 100, NULL),
(2, '500g 装', 12.90, 200, NULL),
(2, '1kg 装', 22.90, 150, NULL),
(3, '5斤装', 68.00, 100, NULL),
(3, '10斤装', 128.00, 80, NULL),
(4, '30枚装', 28.00, 80, NULL),
(4, '60枚装', 52.00, 60, NULL),
(5, '500g 装', 58.00, 50, NULL),
(5, '1000g 装', 108.00, 30, NULL);
