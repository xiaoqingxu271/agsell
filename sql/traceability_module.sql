-- ============================================================
-- 产地溯源模块建表脚本
-- 业务规则：
--   1. 一个商品可挂多个溯源批次，用户端展示最新批次；
--   2. 批次号唯一（uk_batch_no），生成后不可修改；
--   3. 删除溯源信息时，同一事务内级联逻辑删除该批次生产记录；
--   4. 二维码图片上传 OSS，库内仅存 URL（qr_code_url）。
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 溯源信息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `traceability_info`;
CREATE TABLE `traceability_info` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`          BIGINT        NOT NULL COMMENT '商品ID',
    `batch_no`            VARCHAR(50)   NOT NULL COMMENT '批次号（唯一）',
    `farmer_name`         VARCHAR(50)   DEFAULT NULL COMMENT '种植户姓名',
    `farmer_phone`        VARCHAR(20)   DEFAULT NULL COMMENT '种植户电话',
    `origin_province`     VARCHAR(50)   DEFAULT NULL COMMENT '原产省份',
    `origin_city`         VARCHAR(50)   DEFAULT NULL COMMENT '原产城市',
    `origin_district`     VARCHAR(50)   DEFAULT NULL COMMENT '原产区县',
    `planting_date`       DATE          DEFAULT NULL COMMENT '种植日期',
    `harvest_date`        DATE          DEFAULT NULL COMMENT '采摘日期',
    `quality_check_result` VARCHAR(200) DEFAULT NULL COMMENT '质检结果',
    `pesticide_test`      VARCHAR(200)  DEFAULT NULL COMMENT '农药残留检测结果',
    `certification_type`  VARCHAR(20)   DEFAULT NULL COMMENT '认证类型 ORGANIC=有机 GREEN=绿色 GEOGRAPHICAL=地理标志 NONE=无',
    `certification_urls`  TEXT          DEFAULT NULL COMMENT '认证证书图片(JSON数组)',
    `qr_code_url`         VARCHAR(255)  DEFAULT NULL COMMENT '溯源二维码URL',
    `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='溯源信息表';

-- ------------------------------------------------------------
-- 生产记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `production_record`;
CREATE TABLE `production_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`    BIGINT       NOT NULL COMMENT '商品ID（冗余，便于按商品查询）',
    `batch_no`      VARCHAR(50)  NOT NULL COMMENT '批次号',
    `record_type`   VARCHAR(20)  NOT NULL COMMENT '记录类型 SEEDING=播种 FERTILIZING=施肥 WATERING=浇水 PEST_CONTROL=病虫害防治 HARVEST=采收 OTHER=其他',
    `record_date`   DATE         NOT NULL COMMENT '记录日期',
    `content`       VARCHAR(500) DEFAULT NULL COMMENT '记录内容',
    `images`        TEXT         DEFAULT NULL COMMENT '记录图片(JSON数组)',
    `operator`      VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_batch_no` (`batch_no`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产记录表';

-- ------------------------------------------------------------
-- 示例数据（演示用，可按需删除）
-- ------------------------------------------------------------
-- INSERT INTO `traceability_info`
--     (`product_id`, `batch_no`, `farmer_name`, `farmer_phone`, `origin_province`, `origin_city`, `origin_district`,
--      `planting_date`, `harvest_date`, `quality_check_result`, `pesticide_test`, `certification_type`)
-- VALUES
--     (1, 'B202609110001', '李四', '13800000000', '江西省', '赣州市', '信丰县',
--      '2026-03-10', '2026-09-08', '抽检合格', '农残未检出', 'GEOGRAPHICAL');
--
-- INSERT INTO `production_record`
--     (`product_id`, `batch_no`, `record_type`, `record_date`, `content`, `operator`)
-- VALUES
--     (1, 'B202609110001', 'SEEDING', '2026-03-10', '完成定植，株距 3m', '李四'),
--     (1, 'B202609110001', 'FERTILIZING', '2026-05-20', '施用有机肥', '李四'),
--     (1, 'B202609110001', 'HARVEST', '2026-09-08', '人工采摘，分拣装箱', '李四');
