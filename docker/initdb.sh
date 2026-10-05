#!/bin/bash
# 按依赖顺序执行建表脚本（跳过 fix_category_ids.sql：它是数据修复脚本，依赖 product_category 表，全新库初始化时执行会因缺表中断）
# 强制 utf8mb4 连接，避免 UTF-8 中文被 latin1 误解导致双重编码乱码
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/create_table.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/product_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/order_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/after_sales_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/review_banner_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/seckill_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/coupon_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/search_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/system_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/traceability_module.sql
# 优惠券二期：折扣券/品类券（加 discount/max_discount 列 + coupon_product 表）
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/migration/coupon_type_extension.sql
