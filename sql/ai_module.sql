-- ============================================================
-- AI 知识库模块数据库变更（RAG 知识库数据源）
-- 兼容 MySQL 5.7 / 8.x；可重复执行（幂等）
--
-- 说明：本表此前只由 ai-service/scripts/init_faq.py 通过 SQLAlchemy create_all 建立，
-- sql/ 目录里没有对应脚本，导致「22 张表」里有一张不在建表脚本体系内：
-- docker compose 首次启动时 mysql 只自动执行 sql/ 下的脚本，知识库表要等
-- `docker compose exec ai-service python scripts/init_faq.py --index` 才存在。
-- 现补齐本脚本，使建表体系完整；向量索引（Chroma）仍由 init_faq.py / 管理端重建触发。
--
-- 表结构与生产库实测一致（mysqldump --no-data agsell ai_faq 核对）；
-- 刻意不加额外索引，保持与本脚本、init_faq.py 的 SQLAlchemy create_all 三条路径完全同构。
-- ============================================================

USE `agsell`;

-- 1. FAQ 知识库表（RAG 检索与关键词召回的唯一数据源，MySQL 为准、Chroma 为索引）
-- 当前内容三类（category 区分）：
--   general / shipping / aftersales / product / platform —— 36 条平台规则类（人工撰写）
--   product_knowledge   —— 每个在售商品 1 条（卖点/产地/储存/保质期），由 seed_kb_extended.py 生成
--   category_knowledge  —— 每个二级类目 1 条（在售款数与价格带）
CREATE TABLE IF NOT EXISTS `ai_faq` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question`   VARCHAR(255) NOT NULL COMMENT '问题',
    `answer`     TEXT         NOT NULL COMMENT '答案',
    `category`   VARCHAR(50)  NOT NULL COMMENT '分类：shipping/aftersales/product/platform/product_knowledge/category_knowledge 等',
    `keywords`   VARCHAR(255) NOT NULL COMMENT '关键词，逗号分隔，用于关键词召回通道',
    `status`     SMALLINT     NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用即从向量索引删除）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库 FAQ（RAG 检索数据源）';

-- 2. 会话记忆不入库：LangGraph checkpointer 走 SQLite（ai-service/data/checkpoints.db），
--    MEMORY_BACKEND=redis 时用 Redis。故本模块无会话表。

-- 3. 初始化数据（36 条平台规则 FAQ）由脚本写入，不在本 SQL 中重复维护：
--    cd ai-service && python scripts/init_faq.py --index
--    商品域知识条目扩容：python scripts/seed_kb_extended.py
