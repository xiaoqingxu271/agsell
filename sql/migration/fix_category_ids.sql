-- ============================================================
-- 修复分类与商品的外键引用（旧自增ID → 雪花ID）
-- 同时修正部分商品分类归属错误
-- ============================================================

USE `agsell`;

-- ------------------------------------------------------------
-- 1. 修复二级分类的 parent_id
--    旧自增ID映射：1=水果, 2=蔬菜, 3=粮油调味, 4=肉禽蛋奶, 5=干货零食
-- ------------------------------------------------------------

-- citrus (旧parent_id=1) → 水果
UPDATE `product_category` SET `parent_id` = 2096475047344336800
WHERE `id` = 2096475047344336820 AND `parent_id` = 1;

-- 浆果类 (旧parent_id=1) → 水果
UPDATE `product_category` SET `parent_id` = 2096475047344336800
WHERE `id` = 2096475047344336829 AND `parent_id` = 1;

-- 热带水果 (旧parent_id=1) → 水果
UPDATE `product_category` SET `parent_id` = 2096475047344336800
WHERE `id` = 2096475047344336824 AND `parent_id` = 1;

-- 叶菜类 (旧parent_id=2) → 蔬菜
UPDATE `product_category` SET `parent_id` = 2096475047344336871
WHERE `id` = 2096475047344336825 AND `parent_id` = 2;

-- 根茎类 (旧parent_id=2) → 蔬菜
UPDATE `product_category` SET `parent_id` = 2096475047344336871
WHERE `id` = 2096475047344336823 AND `parent_id` = 2;

-- 菌菇类 (旧parent_id=2) → 蔬菜
UPDATE `product_category` SET `parent_id` = 2096475047344336871
WHERE `id` = 2096475047344336881 AND `parent_id` = 2;

-- 大米杂粮 (旧parent_id=3) → 粮油调味
UPDATE `product_category` SET `parent_id` = 2096475047344336861
WHERE `id` = 2096475047344336899 AND `parent_id` = 3;

-- 食用油 (旧parent_id=3) → 粮油调味
UPDATE `product_category` SET `parent_id` = 2096475047344336861
WHERE `id` = 2096475047344336897 AND `parent_id` = 3;

-- 调味品 (旧parent_id=3) → 粮油调味
UPDATE `product_category` SET `parent_id` = 2096475047344336861
WHERE `id` = 2096475047344336896 AND `parent_id` = 3;

-- 猪肉 (旧parent_id=4) → 肉禽蛋奶
UPDATE `product_category` SET `parent_id` = 2096475047344336851
WHERE `id` = 2096475047344336895 AND `parent_id` = 4;

-- 禽类 (旧parent_id=4) → 肉禽蛋奶
UPDATE `product_category` SET `parent_id` = 2096475047344336851
WHERE `id` = 2096475047344336894 AND `parent_id` = 4;

-- 蛋类 (旧parent_id=4) → 肉禽蛋奶
UPDATE `product_category` SET `parent_id` = 2096475047344336851
WHERE `id` = 2096475047344336893 AND `parent_id` = 4;

-- 坚果炒货 (旧parent_id=5) → 干货零食
UPDATE `product_category` SET `parent_id` = 2096475047344336841
WHERE `id` = 2096475047344336892 AND `parent_id` = 5;

-- 蜜饯果干 (旧parent_id=5) → 干货零食
UPDATE `product_category` SET `parent_id` = 2096475047344336841
WHERE `id` = 2096475047344336891 AND `parent_id` = 5;

-- ------------------------------------------------------------
-- 2. 修复商品的 category_id
--    同时修正种子数据中分类归属错误的商品
-- ------------------------------------------------------------

-- 赣南脐橙: 旧category_id=7 → citrus (正确)
UPDATE `product` SET `category_id` = 2096475047344336820
WHERE `id` = 2096479314092355582 AND `category_id` = 7;

-- 有机西兰花: 旧category_id=13(大米杂粮,错误) → 叶菜类 (修正)
UPDATE `product` SET `category_id` = 2096475047344336825
WHERE `id` = 2096479314092355583 AND `category_id` = 13;

-- 五常大米: 旧category_id=19(坚果炒货,错误) → 大米杂粮 (修正)
UPDATE `product` SET `category_id` = 2096475047344336899
WHERE `id` = 2096479314092355584 AND `category_id` = 19;

-- 土鸡蛋: 旧category_id=18 → 蛋类 (正确)
UPDATE `product` SET `category_id` = 2096475047344336893
WHERE `id` = 2096479314092355585 AND `category_id` = 18;

-- 旺仔牛奶: 旧category_id=6(特产农产品) → 特色饮品 (修正,更贴切)
UPDATE `product` SET `category_id` = 2096475047344336898
WHERE `id` = 2096479314092355586 AND `category_id` = 6;

-- 农家腊肉: 旧category_id=17(禽类,错误) → 特产农产品 (修正)
UPDATE `product` SET `category_id` = 2096475047344336831
WHERE `id` = 2096479314092355587 AND `category_id` = 17;
