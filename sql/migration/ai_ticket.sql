-- ============================================================
-- AI 转人工工单（增量脚本）
-- 背景：此前 escalate_human 只回复一句电话话术，不落任何记录，
-- 管理端无从跟进。现在 Python 在触发转人工时回调
-- POST /api/ai/internal/ticket 落一张工单，管理后台「AI 工单」页处理。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 游客（userId 为空）转人工同样要留痕，故 user_id 不设 NOT NULL。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `ai_ticket` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '用户ID（游客转人工为 NULL）',
    `session_id`    VARCHAR(64)  NOT NULL COMMENT 'AI 会话号（回查会话上下文用）',
    `question`      VARCHAR(500) NOT NULL COMMENT '触发转人工的用户原话',
    `reason`        VARCHAR(255) DEFAULT NULL COMMENT '转人工原因（工具入参/规则判定）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理',
    `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `handle_by`     BIGINT       DEFAULT NULL COMMENT '处理管理员ID',
    `handle_time`   DATETIME     DEFAULT NULL COMMENT '处理时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_create` (`status`, `create_time`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 转人工工单';
