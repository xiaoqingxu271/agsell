-- ============================================================
-- 首页公告（增量脚本，P2 §5.9.2）
-- 管理端「公告管理」维护条目，小程序首页展示公告条（轮播式滚动）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；种子数据幂等插入。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `sys_notice` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '公告内容（点开查看）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越大越前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=隐藏 1=展示',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页公告';

-- 种子数据（幂等：按 title 判断）
INSERT INTO `sys_notice` (`title`, `content`, `sort`, `status`)
SELECT '产地直发季', '当季水果产地直发，坏果包赔；满 99 元全国包邮。', 30, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_notice` WHERE `title` = '产地直发季');

INSERT INTO `sys_notice` (`title`, `content`, `sort`, `status`)
SELECT '售后无忧', '品质问题 48 小时内提交售后，坏果包赔快速退款。', 20, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_notice` WHERE `title` = '售后无忧');
