-- ============================================================
-- 用户搜索词记录（增量脚本）
-- 背景：搜索词是高意图偏好信号（用户主动输入"砂糖橘"就是在表达当下的品类需求），
-- 此前只累计到全站热词表（search_hot_word），没有按用户留存。
-- 本表按 (user_id, keyword, 时间) 记录登录用户的搜索词，
-- ai-service 的 preference_repo.search_category_weights 用它把关键词映射到品类权重：
--   关键词 LIKE 商品名 → 命中的在售商品类目计数（近 REC_SEARCH_SIGNAL_DAYS 天）。
-- 游客搜索不落库（无 user_id）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

USE `agsell`;

CREATE TABLE IF NOT EXISTS `user_search_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `keyword`     VARCHAR(50)  NOT NULL COMMENT '搜索词（与搜索接口 50 字上限一致）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户搜索词记录（AI 偏好信号）';
