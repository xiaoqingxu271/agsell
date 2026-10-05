-- =============================================================
-- 评价情感分析模块：review 表新增情感字段
-- 1) sentiment_label   情感标签：1=好评 0=中评 -1=差评（NULL=未分析）
-- 2) sentiment_score   情感得分 0~1，越大越正面
-- 3) sentiment_keywords 情感关键词（逗号分隔，最多 5 个）
-- 分析由 Python AI 服务执行（词库+否定规则），管理端触发同步：
-- POST /api/admin/review/sentiment/sync
-- =============================================================

USE `agsell`;

ALTER TABLE `review`
    ADD COLUMN `sentiment_label`    TINYINT      DEFAULT NULL COMMENT '情感标签 1=好评 0=中评 -1=差评（NULL=未分析）' AFTER `rating`,
    ADD COLUMN `sentiment_score`    DECIMAL(3,2) DEFAULT NULL COMMENT '情感得分 0~1，越大越正面' AFTER `sentiment_label`,
    ADD COLUMN `sentiment_keywords` VARCHAR(200) DEFAULT NULL COMMENT '情感关键词（逗号分隔，最多5个）' AFTER `sentiment_score`;

-- 口碑摘要聚合索引：按商品聚合情感分布
CREATE INDEX `idx_review_product_sentiment` ON `review` (`product_id`, `sentiment_label`);
