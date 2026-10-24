-- ============================================================
-- ai_faq 全文索引（增量脚本）：BM25 全文召回通道的基础设施
-- 背景：检索器此前的"关键词通道"是把全部启用 FAQ 拉到内存做 keywords 子串匹配，
-- 无法利用问法/答案正文里的词面证据。本索引（ngram 解析器，中文 2-gram）让
-- MySQL 按 BM25 相关度从 question/answer/keywords 三列召回候选，
-- 检索器再做词面校验与 RRF 三路融合（向量 + 全文 + 关键词）。
--
-- 前置：ai_faq 表已存在（sql/ai_module.sql 或 init_faq.py 均可）。
-- 幂等：information_schema 查索引是否存在，已存在则跳过。
-- ============================================================

USE `agsell`;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_faq'
      AND index_name = 'ft_ai_faq_question_answer_keywords'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `ai_faq` ADD FULLTEXT INDEX `ft_ai_faq_question_answer_keywords` (`question`, `answer`, `keywords`) WITH PARSER ngram',
    'SELECT ''ft_ai_faq_question_answer_keywords already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
