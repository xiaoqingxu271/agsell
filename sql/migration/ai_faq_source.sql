-- ============================================================
-- ai_faq 增加来源列（增量脚本）：管理端"上传文档切块入库"的基础设施
--
-- 背景：知识库条目此前全部来自手写 FAQ 与商品/溯源派生脚本，source 概念不存在。
-- 文档上传后每块是一条 ai_faq（category=doc_knowledge），需要按"来源文档"整批
-- 替换/删除（同名重复上传=幂等替换），source 列记 doc:{文档名}。
--
-- 前置：ai_faq 表已存在（sql/ai_module.sql 或 init_faq.py 均可）。
-- 幂等：information_schema 查列是否存在，已存在则跳过。
-- Python 侧新装环境无需此脚本：ORM create_all 会直接带出该列。
-- ============================================================

USE `agsell`;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_faq'
      AND column_name = 'source'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `ai_faq` ADD COLUMN `source` VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''来源：空=手工/脚本维护，doc:xxx=管理端上传文档切块入库'' AFTER `keywords`',
    'SELECT ''column ai_faq.source already exists'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
