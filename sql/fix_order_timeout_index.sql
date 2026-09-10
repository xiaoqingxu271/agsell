-- ============================================================
-- 修复：订单超时取消查询的索引（联合索引替换单列 idx_status）
-- 背景：超时任务执行 WHERE status = 0 AND create_time < ? 的等值+范围查询，
--       原 idx_status / idx_create_time 为两列独立索引，只能命中其一，
--       数据量大时可能退化为大范围扫描。
-- 说明：
--  1. 先加后删：先创建联合索引，再删除旧单列索引，全程无查询空窗。
--  2. idx_status_create_time 的最左前缀 (status) 已覆盖原 idx_status 的
--     纯状态查询（如后台按状态筛选），故可安全删除 idx_status。
--  3. 保留 idx_create_time：联合索引无法覆盖"仅按创建时间"的查询。
-- ============================================================

USE `agsell`;

ALTER TABLE `order`
    ADD KEY `idx_status_create_time` (`status`, `create_time`);

ALTER TABLE `order`
    DROP KEY `idx_status`;
