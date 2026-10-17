#!/usr/bin/env python3
"""按 manifest + 上传得到的 OSS URL 生成可直接执行的灌库 SQL。

用法：
  python tools/seed-catalog/gen_sql.py --manifest tools/seed-catalog/manifest.sample.json --out sql/seed_catalog.sql

生成内容（顺序执行即可清空并重建商品域演示数据）：
  1. 清空商品域及其依赖表（分类/商品/规格/轮播/评价/秒杀/溯源/购物车/订单/品类券关联）
  2. 写入分类（含图标 URL）、商品（含主图 + 图集 JSON + 详情 HTML）、规格（含图）、轮播图
  3. manifest.demo 里的评价 / 溯源档案 + 生产记录 / 秒杀活动（引用新商品 ID，保证详情页口碑与溯源可演示）

manifest 里的分类/商品短 ID（1001 / 20001）只作为素材文件名与图集 key 的目录主键，
入库前一律经 snowflake() 换成与后端 IdType.ASSIGN_ID 同布局的 19 位雪花 ID。

时间字段（商品采摘日、溯源档案/生产记录的日期与批次号）不写 manifest 里的绝对日期，
一律改写为相对 CURDATE 的表达式，理由见 harvest_age_days()。
"""
import json
import math
import re
import sys
from datetime import datetime
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "out"

# ---------- 雪花 ID：与后端 IdType.ASSIGN_ID（MyBatis-Plus IdWorker）同布局 ----------
# 真实运行时 ID 由服务端生成，种子数据必须给出确定值才能互相引用，
# 因此按「固定时间戳 + 表间秒位 + 灌库顺序 sequence」推导，重复执行结果一致。
TW_EPOCH_MS = 1288834974657  # MyBatis-Plus IdWorker.twepoch
SEED_DATACENTER = 14
SEED_WORKER = 9
SEED_TS_MS = int(datetime(2026, 10, 11, 10, 0, 0).timestamp() * 1000)
TABLE_SLOT = {"product_category": 0, "product": 1, "product_spec": 2}
SEQ_MAX = 4095  # 雪花 sequence 只有 12 位


def snowflake(table: str, seq: int) -> int:
    ms = SEED_TS_MS + TABLE_SLOT[table] * 1000
    if not 0 <= seq <= SEQ_MAX:
        sys.exit(f"[ERR] {table} 序号 {seq} 超出雪花 sequence 上限 {SEQ_MAX}")
    return ((ms - TW_EPOCH_MS) << 22) | (SEED_DATACENTER << 17) | (SEED_WORKER << 12) | seq


def build_id_map(manifest: dict) -> dict:
    """目录内固定短 ID -> 雪花 ID。短 ID 只用于素材文件名，不入库。"""
    cat = {c["id"]: snowflake("product_category", i) for i, c in enumerate(manifest["categories"], 1)}
    prod = {p["id"]: snowflake("product", i) for i, p in enumerate(manifest["products"], 1)}
    return {"category": cat, "product": prod}


def remap_link(link: str, idmap: dict) -> str:
    """把轮播链接里的目录短 ID 换成入库后的雪花 ID。"""
    m = re.search(r"[?&]id=(\d+)", link or "")
    if not m:
        return link
    short = int(m.group(1))
    if short not in idmap["product"]:
        sys.exit(f"[ERR] 轮播链接引用了不存在的商品短 ID：{link}")
    return link[: m.start(1)] + str(idmap["product"][short]) + link[m.end(1) :]


def remap_product_id(raw, idmap: dict) -> int:
    short = int(raw)
    if short not in idmap["product"]:
        sys.exit(f"[ERR] 演示数据引用了不存在的商品短 ID：{short}")
    return idmap["product"][short]


def remap_category_id(raw, idmap: dict) -> int:
    short = int(raw)
    if short == 0:
        return 0
    if short not in idmap["category"]:
        sys.exit(f"[ERR] 商品引用了不存在的分类短 ID：{short}")
    return idmap["category"][short]


# ---------- 时间字段相对化（采摘日期 / 溯源批次） ----------
# manifest 里的 harvest_date 是"生成素材那天"的绝对日期：种子 SQL 隔一周再导入，
# 全部商品凭空变陈一周，且方向是反的——短保生鲜（保质期 3~7 天）新鲜度直接归 0，
# 耐储粮油（18 个月）反而拿到 0.9 高分，时令引擎的排序结果与产品目标完全相反。
# 因此入库时改写为 DATE_SUB(CURDATE(), INTERVAL n DAY)，n 由"保质期已消耗比例"推出：
# - 比例随保质期长度上升（供应链常识：日配生鲜隔天摘，粮油年度收购），
#   于是短保货新鲜度高、耐储货中等，梯度可讲可演示；
# - 再按商品 ID 确定性散列抖动 ±10%，同类目不至于齐刷刷同分，重复生成结果一致；
# - 上限 75%，任何商品导入当天都还没过保。
_UNIT_DAYS = {"天": 1, "日": 1, "周": 7, "月": 30, "年": 365}
_NUM = re.compile(r"(\d+)")
_RATIO_FLOOR = 0.02
_RATIO_CAP = 0.75
_RATIO_JITTER = 0.10


def shelf_life_days(shelf_life) -> int | None:
    if not shelf_life:
        return None
    m = _NUM.search(str(shelf_life))
    if not m:
        return None
    n = int(m.group(1))
    for unit, days in _UNIT_DAYS.items():
        if unit in str(shelf_life):
            return n * days
    return n  # 纯数字无单位：按天理解


def consumed_ratio(short_id: int, days: int) -> float:
    """保质期已消耗比例：随保质期对数上升 + ID 抖动，落在 2%~75%"""
    span = math.log10(730) - math.log10(3)
    center = 0.05 + 0.50 * (math.log10(min(max(days, 1), 730)) - math.log10(3)) / span
    jitter = (((short_id * 2654435761) % 101) - 50) / 100.0 * _RATIO_JITTER
    return min(max(center + jitter, _RATIO_FLOOR), _RATIO_CAP)


def harvest_age_days(short_id: int, shelf_life) -> int | None:
    """商品采摘日距今多少天；保质期解析不出来返回 None，保留原绝对日期"""
    days = shelf_life_days(shelf_life)
    if not days or days <= 0:
        return None
    return max(int(round(consumed_ratio(short_id, days) * days)), 0)


def sql_date_days_ago(days: int) -> str:
    return f"DATE_SUB(CURDATE(), INTERVAL {int(days)} DAY)"


def sql_date_days_ago(days: int) -> str:
    return f"DATE_SUB(CURDATE(), INTERVAL {int(days)} DAY)"


def rolled_group_date(value, anchor: datetime, anchor_age: int) -> str:
    """把溯源组内的绝对日期整体平移：锚点（该组采摘日）落在 anchor_age 天前，组内间隔原样保留

    采摘之后的记录（质检/装箱）在 anchor_age 很小时会被 clamp 到今天就地发生，
    宁可在当天补记，也不写未来日期——未来日期会让溯源页出现"还没发生"的记录。
    """
    offset = (datetime.strptime(str(value), "%Y-%m-%d") - anchor).days
    return sql_date_days_ago(max(anchor_age - offset, 0))


def rolled_batch_no(batch_no: str, anchor_age: int) -> str:
    """批次号里的日期段（B202609110001）跟着采摘日一起滚动，避免批次号与日期各说一套"""
    m = re.match(r"^B(\d{8})(\d+)$", batch_no or "")
    if not m:
        return q(batch_no)
    return f"CONCAT('B', DATE_FORMAT({sql_date_days_ago(anchor_age)}, '%Y%m%d'), {q(m.group(2))})"


def rolled_harvest(short_id: int, p: dict) -> tuple[str, int | None]:
    """(SQL 日期表达式, 距今天数)；无法滚动时回落 manifest 原始值"""
    age = harvest_age_days(short_id, p.get("shelf_life"))
    if age is None:
        return q(p.get("harvest_date")), None
    return sql_date_days_ago(age), age


CLEAR_TABLES = [
    "product_spec",
    "product",
    "product_category",
    "banner",
    "review",
    "seckill_activity",
    "production_record",
    "traceability_info",
    "cart",
    "order_item",
    "`order`",
    "after_sales",
    "coupon_product",
]


def q(v) -> str:
    """SQL 字面量：字符串转义，数字原样，None -> NULL。"""
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def desc_html(p: dict, urls: list) -> str:
    parts = [
        f"<p>【{p['name']}】{p.get('subtitle', '')}。产地直采，{p.get('origin', '优选产区')}发货，"
        f"{p.get('storage', '常温保存')}，保质期 {p.get('shelf_life', '见包装')}。</p>",
        "<p>采收后 24 小时内预冷分拣，全程冷链/保湿包装，坏果包赔；支持产地溯源批次查询。</p>",
    ]
    for u in urls[1:]:
        parts.append(f'<img src="{u}" style="width:100%;display:block" />')
    return "".join(parts)


def build(manifest: dict, urls: dict, idmap: dict) -> str:
    lines = [
        "-- ============================================================ ",
        "-- 商品域演示数据（由 tools/seed-catalog/gen_sql.py 生成，请勿手改）",
        f"-- 生成时间：{datetime.now():%Y-%m-%d %H:%M}  图片资源：阿里云 OSS 公共读 URL",
        "-- 用法：mysql -h 127.0.0.1 -uroot -proot agsell < sql/seed_catalog.sql",
        "-- 幂等：先清空商品域及依赖表，再按固定主键整批重建。",
        "-- 主键：分类/商品/规格 ID 为雪花 ID（与后端 IdType.ASSIGN_ID 同布局，按目录顺序确定性分配）。",
        "-- ============================================================",
        "",
        "USE `agsell`;",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "",
    ]
    lines += [f"DELETE FROM {t};" for t in CLEAR_TABLES]
    lines += ["SET FOREIGN_KEY_CHECKS = 1;", ""]

    # ---------- 分类 ----------
    cats = manifest["categories"]
    lines.append("-- ---------- 商品分类（含 OSS 图标 URL，主键为雪花 ID） ----------")
    rows = []
    for c in cats:
        icon = urls.get(f"icon:{c['id']}")
        if not icon:
            sys.exit(f"[ERR] 分类 {c['id']} {c['name']} 缺少图标 URL，请先跑 upload_oss.py")
        rows.append(
            f"    ({idmap['category'][c['id']]}, {q(c['name'])}, {q(icon)}, "
            f"{remap_category_id(c.get('parent_id', 0), idmap)}, "
            f"{c.get('sort', 0)}, 1, 0)"
        )
    lines.append(
        "INSERT INTO `product_category` (`id`, `name`, `icon`, `parent_id`, `sort`, `status`, `deleted`) VALUES\n"
        + ",\n".join(rows)
        + ";\n"
    )

    # ---------- 商品 ----------
    lines.append("-- ---------- 商品（主图 + 图集 JSON + 详情 HTML，主键为雪花 ID） ----------")
    lines.append("-- 采摘日期滚动生成：DATE_SUB(CURDATE(), INTERVAL n DAY)，n 随保质期长短递增（越短保存得越久不划算，转得越快）")
    p_rows = []
    harvest_age_by_short: dict[int, int] = {}
    for p in manifest["products"]:
        photos = [urls.get(f"photo:{p['id']}:{i}") for i in range(len(p["photos"]))]
        if not photos:
            sys.exit(f"[ERR] 商品 {p['id']} {p['name']} 没有任何图片，请先补齐 photos.full.json")
        if not all(photos):
            sys.exit(f"[ERR] 商品 {p['id']} {p['name']} 图集不完整，请先跑 upload_oss.py")
        images_json = json.dumps(photos, ensure_ascii=False)
        harvest_sql, harvest_age = rolled_harvest(p["id"], p)
        if harvest_age is not None:
            harvest_age_by_short[p["id"]] = harvest_age
        p_rows.append(
            (
                "    ({id}, {name}, {sub}, {cat}, {price}, {oprice}, {stock}, {sales}, "
                "{main}, {images}, {desc}, {origin}, {harvest}, {shelf}, {storage}, 1, {sort}, 0)"
            ).format(
            id=idmap["product"][p["id"]],
            name=q(p["name"]),
            sub=q(p.get("subtitle")),
            cat=remap_category_id(p["category_id"], idmap),
            price=p["price"],
            oprice=p.get("original_price") or "NULL",
            stock=p.get("stock", 0),
            sales=p.get("sales", 0),
            main=q(photos[0]),
            images=q(images_json),
            desc=q(desc_html(p, photos)),
            origin=q(p.get("origin")),
            harvest=harvest_sql,
            shelf=q(p.get("shelf_life")),
            storage=q(p.get("storage")),
            sort=p.get("sort", 0),
            )
        )
    lines.append(
        "INSERT INTO `product` (`id`, `name`, `subtitle`, `category_id`, `price`, `original_price`, `stock`, `sales`,"
        " `main_image`, `images`, `description`, `origin`, `harvest_date`, `shelf_life`, `storage`, `status`, `sort`, `deleted`) VALUES\n"
        + ",\n".join(p_rows)
        + ";\n"
    )

    # ---------- 规格 ----------
    spec_rows = []
    spec_seq = 0
    lines.append("-- ---------- 商品规格（规格图复用商品图集首图，主键为雪花 ID） ----------")
    for p in manifest["products"]:
        main = urls.get(f"photo:{p['id']}:0")
        for s in p.get("specs", []):
            spec_seq += 1
            spec_rows.append(
                f"    ({snowflake('product_spec', spec_seq)}, {idmap['product'][p['id']]}, "
                f"{q(s['name'])}, {s['price']}, {s.get('stock', 0)}, {q(main)}, 0)"
            )
    if spec_rows:
        lines.append(
            "INSERT INTO `product_spec` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `deleted`) VALUES\n"
            + ",\n".join(spec_rows)
            + ";\n"
        )

    # ---------- 轮播 ----------
    if manifest.get("banners"):
        lines.append("-- ---------- 首页轮播图 ----------")
        b_rows = []
        for b in manifest["banners"]:
            u = urls.get(f"banner:{b['id']}")
            if not u:
                sys.exit(f"[ERR] 轮播 {b['id']} 缺少图片 URL")
            link = remap_link(b.get("link") or "", idmap)
            b_rows.append(f"    ({b['id']}, {q(b['title'])}, {q(u)}, {q(link)}, {b.get('sort', 0)}, 1, 0)")
        lines.append(
            "INSERT INTO `banner` (`id`, `title`, `image`, `link`, `sort`, `status`, `deleted`) VALUES\n"
            + ",\n".join(b_rows)
            + ";\n"
        )

    # ---------- 依赖商品的演示数据 ----------
    demo = manifest.get("demo", {})
    if demo.get("reviews"):
        lines.append("-- ---------- 评价展示主体：绑定库内实际存在的演示账号 ----------")
        lines.append(
            "SET @demo_user = IFNULL((SELECT `id` FROM `sys_user` WHERE `username` = '13800138000' ORDER BY `id` LIMIT 1), 0);\n"
        )
        lines.append("-- ---------- 商品评价（情感值按 ai-service 词库口径预计算） ----------")
        rows = []
        for i, rv in enumerate(demo["reviews"]):
            rows.append(
                f"    (2097480000000004{i:03d}, 0, {remap_product_id(rv['product_id'], idmap)}, @demo_user, {rv['rating']}, "
                f"{q(rv['content'])}, {rv['label']}, {rv['score']}, {q(rv['keywords'])}, 0, 0)"
            )
        lines.append(
            "INSERT INTO `review` (`id`, `order_id`, `product_id`, `user_id`, `rating`, `content`,"
            " `sentiment_label`, `sentiment_score`, `sentiment_keywords`, `is_anonymous`, `deleted`) VALUES\n"
            + ",\n".join(rows)
            + ";\n"
        )
    if demo.get("traceability"):
        lines.append("-- ---------- 产地溯源档案 + 生产记录（日期与批次号跟着商品采摘日滚动） ----------")
        t_rows, r_rows = [], []
        rec_id = 0
        for t in demo["traceability"]:
            pid = remap_product_id(t["product_id"], idmap)
            anchor_age = harvest_age_by_short.get(int(t["product_id"]))
            if anchor_age is None:  # 商品没配保质期：溯源日期也保持原绝对值
                plant_sql = q(t["planting_date"])
                harvest_sql = q(t["harvest_date"])
                batch_sql = q(t["batch_no"])

                def rec_sql(d):
                    return q(d)
            else:
                anchor = datetime.strptime(t["harvest_date"], "%Y-%m-%d")
                plant_sql = rolled_group_date(t["planting_date"], anchor, anchor_age)
                harvest_sql = sql_date_days_ago(anchor_age)
                batch_sql = rolled_batch_no(t["batch_no"], anchor_age)

                def rec_sql(d):
                    return rolled_group_date(d, anchor, anchor_age)

            t_rows.append(
                f"    ({t['id']}, {pid}, {batch_sql}, {q(t['farmer_name'])}, {q(t['farmer_phone'])}, "
                f"{q(t['origin_province'])}, {q(t['origin_city'])}, {q(t['origin_district'])}, "
                f"{plant_sql}, {harvest_sql}, {q(t['quality_check_result'])}, "
                f"{q(t['pesticide_test'])}, {q(t['certification_type'])}, 0)"
            )
            for rec in t.get("records", []):
                rec_id += 1
                r_rows.append(
                    f"    ({2097480000000005000 + rec_id}, {pid}, {batch_sql}, {q(rec['type'])}, "
                    f"{rec_sql(rec['date'])}, {q(rec['content'])}, {q(rec['operator'])}, 0)"
                )
        lines.append(
            "INSERT INTO `traceability_info` (`id`, `product_id`, `batch_no`, `farmer_name`, `farmer_phone`,"
            " `origin_province`, `origin_city`, `origin_district`, `planting_date`, `harvest_date`,"
            " `quality_check_result`, `pesticide_test`, `certification_type`, `deleted`) VALUES\n"
            + ",\n".join(t_rows)
            + ";\n"
        )
        if r_rows:
            lines.append(
                "INSERT INTO `production_record` (`id`, `product_id`, `batch_no`, `record_type`, `record_date`,"
                " `content`, `operator`, `deleted`) VALUES\n" + ",\n".join(r_rows) + ";\n"
            )
    if demo.get("seckill"):
        lines.append("-- ---------- 进行中的秒杀活动（起止时间取 NOW() 相对值，重复执行仍有效） ----------")
        rows = []
        for s in demo["seckill"]:
            rows.append(
                f"    ({s['id']}, {q(s['activity_code'])}, {remap_product_id(s['product_id'], idmap)}, {s['seckill_price']}, "
                f"{s['seckill_stock']}, {s['seckill_limit']}, DATE_SUB(NOW(), INTERVAL 1 HOUR), "
                f"DATE_ADD(NOW(), INTERVAL 2 DAY), 1, {s.get('sort', 50)}, 0)"
            )
        lines.append(
            "INSERT INTO `seckill_activity` (`id`, `activity_code`, `product_id`, `seckill_price`, `seckill_stock`,"
            " `seckill_limit`, `start_time`, `end_time`, `status`, `sort`, `deleted`) VALUES\n"
            + ",\n".join(rows)
            + ";\n"
        )

    lines.append(f"-- 统计：分类 {len(cats)} / 商品 {len(manifest['products'])} / 规格 {len(spec_rows)} / 轮播 {len(manifest.get('banners', []))}")
    return "\n".join(lines) + "\n"


def main() -> int:
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out", default=str(HERE.parent.parent / "sql" / "seed_catalog.sql"))
    args = ap.parse_args()

    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    urls = json.loads((HERE / "out" / "urls.json").read_text(encoding="utf-8"))
    idmap = build_id_map(manifest)
    sql = build(manifest, urls, idmap)
    out = Path(args.out)
    out.write_text(sql, encoding="utf-8")

    # 短 ID -> 雪花 ID 对照表：答辩演示脚本、手工验证时按此查真实主键
    (OUT_DIR / "id_map.json").write_text(
        json.dumps(
            {
                "product_category": {str(k): v for k, v in sorted(idmap["category"].items())},
                "product": {str(k): v for k, v in sorted(idmap["product"].items())},
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    first_cat = idmap["category"][manifest["categories"][0]["id"]]
    first_prod = idmap["product"][manifest["products"][0]["id"]]
    print(f"[OK] 生成 {out}（{len(sql.splitlines())} 行）")
    print(f"[OK] 雪花 ID：分类起点 {first_cat} / 商品起点 {first_prod}，对照表 {OUT_DIR / 'id_map.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
