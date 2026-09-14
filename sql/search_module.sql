-- ============================================================
-- 搜索模块数据库变更（MySQL FULLTEXT / ngram 方案）
-- 兼容 MySQL 5.7.6+ / 8.x（ngram 解析器内置）
-- 可重复执行（幂等）
-- ============================================================

-- 1. 搜索热词表
-- 主键使用雪花 ID（与 product/admin 等表一致）：不暴露自增序号，避免被枚举遍历
CREATE TABLE IF NOT EXISTS `search_hot_word` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花ID，应用层生成）',
    `word`         VARCHAR(50) NOT NULL COMMENT '搜索词',
    `search_count` INT         NOT NULL DEFAULT 0 COMMENT '累计搜索次数',
    `sort`         INT         NOT NULL DEFAULT 0 COMMENT '排序值，越大越前',
    `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '0=隐藏 1=展示',
    `is_manual`    TINYINT     NOT NULL DEFAULT 0 COMMENT '0=自动统计 1=管理员手工配置',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME    DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索热词表';

-- 1.1 存量迁移（幂等）：若 id 列仍为 AUTO_INCREMENT（旧版自增表），移除自增属性
SET @auto_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'search_hot_word'
      AND column_name = 'id'
      AND extra LIKE '%auto_increment%'
);
SET @ddl_migrate = IF(@auto_col > 0,
    'ALTER TABLE `search_hot_word` MODIFY COLUMN `id` BIGINT NOT NULL COMMENT ''主键（雪花ID，应用层生成）''',
    'SELECT ''id already non-auto_increment'' AS msg');
PREPARE stmt_migrate FROM @ddl_migrate;
EXECUTE stmt_migrate;
DEALLOCATE PREPARE stmt_migrate;

-- 2. 商品表 ngram 全文索引（幂等：已存在则跳过）
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'product'
      AND index_name = 'ft_product_name_subtitle'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `product` ADD FULLTEXT INDEX `ft_product_name_subtitle` (`name`, `subtitle`) WITH PARSER ngram',
    'SELECT ''ft_product_name_subtitle already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
