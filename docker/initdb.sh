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
# 用户浏览足迹：AI 个性化推荐的实时行为信号（user_product_view 表）
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/behavior_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/system_module.sql
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/traceability_module.sql
# 优惠券二期：折扣券/品类券（加 discount/max_discount 列 + coupon_product 表）
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/migration/coupon_type_extension.sql
# 评价情感分析：review 表加情感标签/得分/关键词三列（口碑摘要依赖，缺列会导致评价接口 SQL 报错）
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/migration/review_sentiment.sql
# AI 转人工工单：escalate_human 触发后由 Python 回调落库，管理后台「AI 工单」页处理
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/migration/ai_ticket.sql
# 用户站内消息：发货/售后处理结果写入，小程序「消息中心」页展示（首页铃铛入口）
mysql --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD" < /sql/migration/user_message.sql
