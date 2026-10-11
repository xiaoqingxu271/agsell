-- =============================================================
-- 答辩演示数据（可选，手动执行；不随 initdb.sh / CI 自动运行）
--
-- 用法：mysql -h 127.0.0.1 -uroot -proot agsell < sql/demo_data.sql
--
-- 内容：演示名义用户 13800138000（评价/地址/订单的展示主体；小程序实际登录走微信授权 mock）+ 收货地址
--
-- 注意：商品域演示数据（分类/商品/规格/轮播/评价/溯源/秒杀）已统一由
--       sql/seed_catalog.sql 生成并灌入（含 OSS 图标与商品图 URL），
--       本脚本不再重复写入，避免出现指向已删除商品 ID 的孤儿评价。
--       执行顺序：create_table.sql → product_module.sql → seed_catalog.sql → demo_data.sql
--
-- 幂等：固定主键 + ON DUPLICATE KEY UPDATE，可重复执行。
-- 主键使用 2097480000000000xxx 专用段，不与模块种子数据冲突。
-- =============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 演示用户与收货地址（seed_catalog.sql 里的评价以 username 反查该用户主键）
-- ------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `phone`, `status`, `deleted`)
VALUES (2097480000000000901, '13800138000', '$2a$10$HQxLgVnzsIO9k3O9egOaJe.UWvW2qihvvUNp7exPA6oSuR0MRx95W',
        '演示用户', '13800138000', 1, 0)
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`), `phone` = VALUES(`phone`), `status` = 1;

SET @demo_user = IFNULL((SELECT `id` FROM `sys_user` WHERE `username` = '13800138000' ORDER BY `id` LIMIT 1), 2097480000000000901);

INSERT INTO `user_address` (`id`, `user_id`, `receiver`, `phone`, `province`, `city`, `district`, `detail`, `is_default`, `tag`, `deleted`)
VALUES (2097480000000000902, @demo_user, '演示用户', '13800138000', '江西省', '赣州市', '章贡区', '蓉江路 88 号 3 栋 201', 1, '家', 0)
ON DUPLICATE KEY UPDATE `receiver` = VALUES(`receiver`), `detail` = VALUES(`detail`), `is_default` = 1;
