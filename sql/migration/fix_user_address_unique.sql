-- ============================================================
-- 修复：删除 user_address 表 uk_user_default 唯一索引
-- 背景：is_default 为 0/1 的 TINYINT，对 (user_id, is_default) 建唯一索引
--       意味着同一用户最多只能有 1 条 is_default=0 的记录，
--       即一个用户最多只能有 2 个地址（1 默认 + 1 非默认），
--       与"收货地址可多条"的业务模型冲突。
-- 影响：用户设置另一地址为默认时，服务端先执行
--       UPDATE user_address SET is_default=0 WHERE user_id=?
--       （将该用户全部地址置为非默认），多条地址同时为 0 时
--       触发 Duplicate entry 唯一键冲突，全局异常捕获后提示"系统错误"。
-- 说明：
--  1. 单用户默认地址唯一性由应用层保证（setDefaultAddress/addAddress/
--     updateAddress 均在事务内先清后设），无需数据库唯一索引兜底。
--  2. 保留 idx_user_id 单列索引：仍覆盖按用户查询地址的场景。
-- ============================================================

USE `agsell`;

ALTER TABLE `user_address`
    DROP INDEX `uk_user_default`;
