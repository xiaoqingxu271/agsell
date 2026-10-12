-- =============================================================
-- 秒杀模块升级：seckill_activity 新增 sold_count（已占名额）
-- ------------------------------------------------------------
-- 背景：原实现把 Redis 库存键当作已售的唯一记录，表里没有 sold_count，
--       一旦 Redis 重启/键被淘汰，-1 补偿路径会按 seckill_stock 总量重建，
--       已抢掉的名额重新放出（活动配额被击穿）。
-- 现在：抢到名额即 DB +1，缓存丢失时按 seckill_stock - sold_count 重建。
-- 全新库无需执行本脚本（sql/seckill_module.sql 的建表语句已含该列）。
-- =============================================================

USE `agsell`;

-- 幂等加列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'agsell'
      AND TABLE_NAME = 'seckill_activity'
      AND COLUMN_NAME = 'sold_count');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `seckill_activity` ADD COLUMN `sold_count` INT NOT NULL DEFAULT 0 COMMENT ''已占名额（DB 侧真相；Redis 库存丢失时按 seckill_stock - sold_count 重建）'' AFTER `seckill_stock`',
    'SELECT ''sold_count 列已存在，跳过加列'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填历史已占名额：秒杀订单中未取消的（status 4=已取消，取消与超时路径都已释放名额）
-- LEAST 兜住"管理员事后调低 seckill_stock"造成的超出，避免重建出负库存
UPDATE `seckill_activity` a
SET a.`sold_count` = LEAST(
        a.`seckill_stock`,
        COALESCE((SELECT COUNT(*)
                  FROM `order` o
                  WHERE o.`seckill_activity_id` = a.`id`
                    AND o.`status` <> 4
                    AND o.`deleted` = 0), 0)
    )
WHERE a.`deleted` = 0;
