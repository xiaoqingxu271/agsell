#!/usr/bin/env python3
"""丰富版种子数据生成：在 gen_sql.py 基础段之上追加全库覆盖的演示数据。

与 gen_sql.py 的关系：
  基础段（分类/商品/规格/轮播 + demo 评价/溯源/秒杀）直接调用 gen_sql.py 生成，
  本脚本在其后追加四类"全库覆盖"数据，让每个商品都能演示出完整效果：
    1. 演示用户 ×8（评价展示主体；BCrypt hash 复用 sql/demo_data.sql 的演示账号）
    2. 商品评价：每个商品 2~4 条（星级分池文案 + 情感值预计算 + 部分匿名），全库 ~600 条
    3. 产地溯源档案 ×1/商品 + 生产记录 ×5/商品（播种/施肥/浇水/植保/采收，日期随采摘日滚动）
    4. 秒杀活动 ×10（进行中 6 / 未开始 2 / 已结束 2，时间全部 NOW() 相对值）

ID 方案：沿用 gen_sql.snowflake()，新增表槽位（sys_user=3 / review=4 /
traceability_info=5 / production_record=6 / seckill_activity=7），
与 gen_sql 的槽位 0/1/2 及 demo 段的字面量 ID（2097480000000002xxx~5xxx）互不重叠。

用法（在 ai-service/.env 配好数据库；本脚本只生成 SQL，不直接连库）：
    python tools/seed-catalog/gen_rich_sql.py
    # 产物：sql/seed_catalog.sql（自清空自重建，可重复执行）
    mysql -h 127.0.0.1 -uroot -proot agsell < sql/seed_catalog.sql
"""
import json
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import gen_sql  # noqa: E402  复用 snowflake / build_id_map / q / rolled_harvest 等

MANIFEST = HERE / "manifest.full.json"
OUT_FILE = HERE.parent.parent / "sql" / "seed_catalog.sql"

# 新增表槽位（与 gen_sql.TABLE_SLOT 的 0/1/2 错开；每槽 1000ms 的 ID 间距保证互不重叠）
SLOT = {"sys_user": 3, "review": 4, "traceability_info": 5, "production_record": 6, "seckill_activity": 7}


def sf(table: str, seq: int) -> int:
    ms = gen_sql.SEED_TS_MS + SLOT[table] * 1000
    return ((ms - gen_sql.TW_EPOCH_MS) << 22) | (gen_sql.SEED_DATACENTER << 17) | (gen_sql.SEED_WORKER << 12) | seq


DEMO_PASSWORD_HASH = "$2a$10$HQxLgVnzsIO9k3O9egOaJe.UWvW2qihvvUNp7exPA6oSuR0MRx95W"

DEMO_USERS = [
    ("13800138001", "山风小筑"),
    ("13800138002", "橙子味的风"),
    ("13800138003", "爱吃柑橘的猫"),
    ("13800138004", "稻田边的少年"),
    ("13800138005", "挑选控阿珍"),
    ("13800138006", "下班买菜中"),
    ("13800138007", "果篮常客"),
    ("13800138008", "舌尖上的原产地"),
]

# 评价文案池：按星级分池，{name} 会被替换成商品名
REVIEW_POOL = {
    5: [
        "{name}太惊喜了，打开箱子就有果香，个头均匀没有坏果，家里老人孩子都爱吃",
        "回购第三次了，{name}品质一直很稳，产地直发就是新鲜，比超市买的强太多",
        "包装很用心，每个都有独立格子防磕碰，{name}到手完完整整，口感清甜多汁",
        "{name}新鲜度满分，看得出来是刚采摘就发的，果肉紧实味道正，值得推荐",
        "送人特有面子，产地溯源码扫一下就能看到采摘记录，{name}吃着放心",
        "客服态度好物流也快，{name}比图片还好看，甜度刚刚好不齁，五星好评",
    ],
    4: [
        "{name}整体不错，个头稍小的有几个，但味道在线，性价比可以",
        "新鲜度可以，包装到位，{name}有一点酸尾，配着吃正好",
        "比预期好，{name}果肉饱满，就是发货慢了半天，总体满意",
        "{name}味道不错，分量实在，下次搞活动再囤一点",
    ],
    3: [
        "{name}中规中矩，有 two 个碰伤了一小块，客服很快处理了，味道还行",
        "一般般吧，{name}个头比想象中小，味道中规中矩，不影响吃",
    ],
    2: [
        "{name}到手有点蔫了，新鲜度不太够，和描述有差距，希望改进运输包装",
    ],
    1: [
        "{name}有三个坏果，联系客服补发了但体验不好，谨慎下单",
    ],
}

# 情感关键词池（按星级，取自评价文本里的高频词）
KEYWORD_POOL = {
    5: "新鲜,好吃,多汁,包装好",
    4: "不错,新鲜,实惠",
    3: "一般,还行",
    2: "不新鲜,有磕碰",
    1: "坏果,失望",
}

FARMERS = ["陈大山", "李秋实", "王小满", "周穗生", "郑果农", "吴有田"]
OPERATORS = ["陈大山", "李秋实", "王小满"]

PRODUCTION_TEMPLATES = [
    ("SEEDING", 150, [
        "完成幼苗定植，株距 {gap}，覆盖地膜保温保墒",
        "选育壮苗移栽大田，浇定根水，搭建遮阳网缓苗",
    ]),
    ("FERTILIZING", 120, [
        "施用腐熟有机肥打底，追施磷钾元素促进坐果",
        "沟施农家肥与饼肥混合基肥，叶面喷施微量元素肥",
    ]),
    ("WATERING", 90, [
        "伏旱期滴灌补水两次，配合中耕除草保墒",
        "早晚各喷灌一次，控制土壤含水量在适宜区间",
    ]),
    ("PEST_CONTROL", 45, [
        "悬挂黄板与生物防治为主，未检出农残超标",
        "释放丽蚜小蜂生物防控，全生育期未使用化学农药",
    ]),
    ("HARVEST", 0, [
        "清晨带露采收，当天预冷分拣入库",
        "分批采收成熟果，全程冷链转运至分拣中心",
    ]),
]


def harvest_gap(seq: int) -> str:
    """生产记录里的种植参数，按商品序号轮换让文案不千篇一律"""
    return ["3m", "40cm", "25cm", "1.2m", "50cm"][seq % 5]


def build_rich(manifest: dict, idmap: dict) -> str:
    lines: list[str] = []
    products = manifest["products"]

    # ---------- 演示用户 ----------
    # 幂等关键：CLEAR_TABLES 故意不清 sys_user（真实用户不能动），所以种子用户段
    # 自己先按用户名删除上一轮的演示账号（只删这 8 个，绝不碰 13800138000 与真实用户）
    lines.append("-- ---------- 演示用户（评价展示主体；BCrypt hash 与 demo 账号一致） ----------")
    lines.append(
        "DELETE FROM `sys_user` WHERE `username` IN ("
        + ", ".join(gen_sql.q(u) for u, _ in DEMO_USERS) + ");\n"
    )
    user_rows = []
    for i, (username, nickname) in enumerate(DEMO_USERS, 1):
        user_rows.append(
            f"    ({sf('sys_user', i)}, {gen_sql.q(username)}, {gen_sql.q(DEMO_PASSWORD_HASH)}, "
            f"{gen_sql.q(nickname)}, NULL, 1, NOW(), NOW(), 0)"
        )
    lines.append(
        "INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `phone`,"
        " `status`, `login_time`, `create_time`, `deleted`) VALUES\n" + ",\n".join(user_rows) + ";\n"
    )
    user_ids = [sf("sys_user", i) for i in range(1, len(DEMO_USERS) + 1)]

    # ---------- 商品评价全覆盖 ----------
    lines.append("-- ---------- 商品评价全覆盖（星级分池文案 + 情感值预计算 + 30% 匿名） ----------")
    rating_cycle = [5, 5, 4, 5, 3, 5, 4, 5, 2, 4, 5, 1]  # 大致 5星67% / 4星17% / 3星8% / 2星8% / 1星8%
    review_rows = []
    r_seq = 0
    for p_seq, p in enumerate(products):
        pid = idmap["product"][p["id"]]
        name = p["name"]
        count = 2 + (p_seq % 3)  # 每商品 2~4 条
        for j in range(count):
            rating = rating_cycle[(p_seq * 3 + j) % len(rating_cycle)]
            pool = REVIEW_POOL[rating]
            content = pool[(p_seq * 2 + j) % len(pool)].replace("{name}", name)
            if rating >= 4:
                label, score = 1, round(0.85 + ((p_seq + j) % 10) * 0.015, 2)
            elif rating == 3:
                label, score = 0, 0.55
            else:
                label, score = -1, round(0.15 + ((p_seq + j) % 5) * 0.03, 2)
            anonymous = 1 if (p_seq + j) % 3 == 0 else 0
            r_seq += 1
            review_rows.append(
                f"    ({sf('review', r_seq)}, 0, {pid}, {user_ids[(p_seq + j) % len(user_ids)]}, {rating}, "
                f"{gen_sql.q(content)}, {label}, {score}, {gen_sql.q(KEYWORD_POOL[rating])}, {anonymous}, 0, "
                f"DATE_SUB(NOW(), INTERVAL {(p_seq * 3 + j * 7) % 672 + 2} HOUR))"
            )
    lines.append(
        "INSERT INTO `review` (`id`, `order_id`, `product_id`, `user_id`, `rating`, `content`,"
        " `sentiment_label`, `sentiment_score`, `sentiment_keywords`, `is_anonymous`, `deleted`, `create_time`) VALUES\n"
        + ",\n".join(review_rows) + ";\n"
    )

    # ---------- 溯源档案 + 生产记录全覆盖 ----------
    lines.append("-- ---------- 溯源档案 ×1/商品 + 生产记录 ×5/商品（日期随采摘日滚动，NOW() 相对值） ----------")
    t_rows, r_rows = [], []
    t_seq = 0
    pr_seq = 0
    for p_seq, p in enumerate(products):
        pid = idmap["product"][p["id"]]
        _, harvest_age = gen_sql.rolled_harvest(p["id"], p)
        harvest_age = harvest_age if harvest_age is not None else 7
        origin = p.get("origin") or "云南昆明"
        province = origin[:3] if origin.startswith(("黑龙江", "内蒙古")) else origin[:2]
        city = origin[len(province):]
        batch = f"PC2026-{p_seq + 1:04d}"
        t_seq += 1
        t_rows.append(
            f"    ({sf('traceability_info', t_seq)}, {pid}, {gen_sql.q(batch)}, {gen_sql.q(FARMERS[p_seq % len(FARMERS)])}, "
            f"{gen_sql.q(f'139{p_seq:08d}')}, {gen_sql.q(province)}, {gen_sql.q(city)}, '', "
            f"DATE_SUB(CURDATE(), INTERVAL {harvest_age + 150} DAY), DATE_SUB(CURDATE(), INTERVAL {harvest_age} DAY), "
            f"{gen_sql.q('农残检测合格')}, {gen_sql.q('未检出')}, "
            f"{gen_sql.q(['绿色食品', '有机转换', '地理标志', '无公害'][p_seq % 4])}, 0)"
        )
        for type_code, days_before, templates in PRODUCTION_TEMPLATES:
            pr_seq += 1
            content = templates[(p_seq + pr_seq) % len(templates)].replace("{gap}", harvest_gap(p_seq))
            day = harvest_age + days_before
            r_rows.append(
                f"    ({sf('production_record', pr_seq)}, {pid}, {gen_sql.q(batch)}, {gen_sql.q(type_code)}, "
                f"DATE_SUB(CURDATE(), INTERVAL {day} DAY), {gen_sql.q(content)}, "
                f"{gen_sql.q(OPERATORS[(p_seq + pr_seq) % len(OPERATORS)])}, 0)"
            )
    lines.append(
        "INSERT INTO `traceability_info` (`id`, `product_id`, `batch_no`, `farmer_name`, `farmer_phone`,"
        " `origin_province`, `origin_city`, `origin_district`, `planting_date`, `harvest_date`,"
        " `quality_check_result`, `pesticide_test`, `certification_type`, `deleted`) VALUES\n"
        + ",\n".join(t_rows) + ";\n"
    )
    lines.append(
        "INSERT INTO `production_record` (`id`, `product_id`, `batch_no`, `record_type`, `record_date`,"
        " `content`, `operator`, `deleted`) VALUES\n" + ",\n".join(r_rows) + ";\n"
    )

    # ---------- 秒杀活动扩充 ----------
    lines.append("-- ---------- 秒杀活动 ×10（进行中 6 / 未开始 2 / 已结束 2，时间 NOW() 相对值） ----------")
    picked = [p for i, p in enumerate(products) if i % 20 == 7][:10]
    s_rows = []
    for i, p in enumerate(picked):
        pid = idmap["product"][p["id"]]
        price = round(p["price"] * 0.7, 1)
        stock = 50 + (i * 7) % 50
        limit = 1 + i % 2
        code = f"SK2026{(i % 4) + 1}{i:02d}"
        if i < 6:       # 进行中
            start, end, status = "DATE_SUB(NOW(), INTERVAL 1 HOUR)", "DATE_ADD(NOW(), INTERVAL 2 DAY)", 1
        elif i < 8:     # 未开始
            start, end, status = "DATE_ADD(NOW(), INTERVAL 3 DAY)", "DATE_ADD(NOW(), INTERVAL 5 DAY)", 0
        else:           # 已结束
            start, end, status = "DATE_SUB(NOW(), INTERVAL 5 DAY)", "DATE_SUB(NOW(), INTERVAL 1 DAY)", 2
        s_rows.append(
            f"    ({sf('seckill_activity', i + 1)}, {gen_sql.q(code)}, {pid}, {price}, {stock}, {limit}, "
            f"{start}, {end}, {status}, {50 + i}, 0)"
        )
    lines.append(
        "INSERT INTO `seckill_activity` (`id`, `activity_code`, `product_id`, `seckill_price`, `seckill_stock`,"
        " `seckill_limit`, `start_time`, `end_time`, `status`, `sort`, `deleted`) VALUES\n"
        + ",\n".join(s_rows) + ";\n"
    )

    lines.append(
        f"-- 丰富段统计：演示用户 {len(DEMO_USERS)} / 评价 {len(review_rows)} / "
        f"溯源档案 {t_seq} / 生产记录 {pr_seq} / 秒杀 {len(s_rows)}"
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    # ① 基础段：调用 gen_sql.py 生成（分类/商品/规格/轮播 + demo 段）
    with tempfile.NamedTemporaryFile(mode="w", suffix=".sql", delete=False, encoding="utf-8") as tmp:
        base_out = tmp.name
    subprocess.run(
        [sys.executable, str(HERE / "gen_sql.py"), "--manifest", str(MANIFEST), "--out", base_out],
        check=True,
    )
    base_sql = Path(base_out).read_text(encoding="utf-8")

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    idmap = gen_sql.build_id_map(manifest)

    # ② 丰富段
    rich_sql = build_rich(manifest, idmap)

    # ③ 合并写入（注意：丰富段依赖基础段的商品 ID，必须整体在后）
    OUT_FILE.write_text(base_sql.rstrip() + "\n\n-- ============================================================\n"
                        "-- ↓↓↓ 丰富段（gen_rich_sql.py 追加）：全库覆盖的评价/溯源/生产记录/秒杀 ↓↓↓\n"
                        "-- ============================================================\n\n"
                        + rich_sql, encoding="utf-8")

    n_cat = len(manifest["categories"])
    n_prod = len(manifest["products"])
    print(f"已生成 {OUT_FILE}")
    print(f"基础段：分类 {n_cat} / 商品 {n_prod}（全部带 OSS 图片）+ 规格/轮播/demo 段")
    print("丰富段：演示用户 8 / 评价全覆盖 / 溯源+生产记录全覆盖 / 秒杀 10")
    return 0


if __name__ == "__main__":
    sys.exit(main())
