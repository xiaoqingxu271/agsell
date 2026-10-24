-- ============================================================
-- 用户站内消息（增量脚本）
-- 背景：小程序首页铃铛此前只弹「暂无新消息」。现在发货、售后处理结果
-- 两个管理员触发的节点写入站内信，用户端「消息中心」页展示并支持已读。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `user_message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
    `type`        VARCHAR(20)  NOT NULL COMMENT '消息类型：order=订单/物流 after_sales=售后结果',
    `title`       VARCHAR(100) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
    `related_no`  VARCHAR(64)  DEFAULT NULL COMMENT '关联单号（订单号/售后单号，用于跳转）',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read_time` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内消息（发货/售后结果通知）';
