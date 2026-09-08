-- ============================================================
-- 售后/退款模块建表脚本
-- 业务规则：
--   1. 仅 待收货(2)/已完成(3) 订单可申请售后；
--   2. 同一订单同一时间仅允许一个进行中的售后单（status=0）；
--   3. 同意退款：订单关闭(status=4)，回补库存，已完成订单回滚销量；
--   4. 拒绝/撤销：订单恢复 original_status。
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 售后单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `after_sales`;
CREATE TABLE `after_sales` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `after_sales_no`  VARCHAR(32)   NOT NULL COMMENT '售后单号',
    `order_id`        BIGINT        NOT NULL COMMENT '订单ID',
    `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单号',
    `user_id`         BIGINT        NOT NULL COMMENT '用户ID',
    `original_status` TINYINT       NOT NULL COMMENT '申请时订单状态 2=待收货 3=已完成',
    `type`            TINYINT       NOT NULL DEFAULT 1 COMMENT '售后类型 1=仅退款 2=退货退款',
    `reason_type`     VARCHAR(20)   NOT NULL DEFAULT 'OTHER' COMMENT '原因类型 QUALITY=品质问题(坏果包赔) WRONG_ITEM=发错货 MISSING_ITEM=少件漏发 OTHER=其他',
    `reason`          VARCHAR(500)  DEFAULT NULL COMMENT '问题描述',
    `images`          TEXT          DEFAULT NULL COMMENT '凭证图片(JSON数组)',
    `refund_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '申请退款金额',
    `status`          TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0=待处理 1=已同意(退款完成) 2=已拒绝 3=已撤销',
    `handle_remark`   VARCHAR(500)  DEFAULT NULL COMMENT '处理意见',
    `handle_by`       BIGINT        DEFAULT NULL COMMENT '处理人(管理员ID)',
    `handle_time`     DATETIME      DEFAULT NULL COMMENT '处理时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_after_sales_no` (`after_sales_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单表';
