-- ============================================================
-- ai_chat_log 对话审计日志（增量脚本）：管理端"对话审计"页的数据源
--
-- 背景：此前对话只在本地 SQLite 做聚合统计（kb_hit_log），管理员看不到逐轮
-- 对话内容，合规审计与"用户反馈说 AI 答错了"的追溯都无从下手。
-- 本表每轮一条（问题原文 + 回复全文 + 意图/工具/拒答/引用/耗时），
-- Python 在每轮收尾旁路写入，Java 管理端直读（与 ai_ticket 同模式）。
--
-- 前置：agsell 库已存在。幂等：CREATE TABLE IF NOT EXISTS。
-- Python 侧新装环境也会兜底建表（chat_audit._ensure_table）。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `ai_chat_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NULL COMMENT '用户 id（游客/未登录为 null）',
    `session_id`  VARCHAR(64)  NOT NULL COMMENT '会话 id',
    `question`    TEXT         NOT NULL COMMENT '用户本轮提问原文',
    `reply`       TEXT         NULL COMMENT 'AI 本轮回复',
    `intent`      VARCHAR(32)  NULL COMMENT '意图标签（规则前置命中才非空）',
    `tools`       VARCHAR(255) NULL COMMENT '本轮调用的工具，逗号分隔',
    `refused`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否拒答轮 0/1',
    `hits`        INT          NOT NULL DEFAULT 0 COMMENT '知识库命中条数',
    `refs`        VARCHAR(1000) NULL COMMENT '引用的 FAQ id，逗号分隔',
    `duration_ms` INT          NULL COMMENT '本轮总耗时毫秒',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chatlog_created` (`created_at`),
    KEY `idx_chatlog_user` (`user_id`),
    KEY `idx_chatlog_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 对话审计日志（每轮一条，Python 落库，管理端只读）';
