"""推荐召回评估：热销榜 vs 关键词 LIKE vs 向量语义召回（商品索引，生产一级通道）

对 40 条人工标注导购问句（覆盖全部主营类目；标注 = 相关二级类目 + 可选价格上限），
在同一在售商品池上跑三条召回通道，取各自 top-5 计算 Hit@1 / Hit@5 / P@5 / MRR，
并统计类目覆盖率。关键词通道模拟生产二级降级路径（品类词 LIKE 商品名/副标题）；
向量通道为真实 bge-small-zh + Chroma(product) 索引，与线上 ProductStore 完全同参。

后续步骤（个性化重排）落地后，可用 --lane personalize 追加第四列对比。

用法（在 ai-service 目录下）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/eval_recommend.py

前置：先重建商品索引（POST /v1/admin/kb/products/index 或本脚本会自动 rebuild）。
输出：控制台报告 + scripts/eval_results/recommend_eval.json
"""
import json
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

TOP_K_METRIC = 5


from _provenance import provenance  # noqa: E402  统一溯源字段

# 标注集：(问句, [相关二级类目], 关键词通道用的品类词(模拟抽取结果, None=无关键词), 价格上限)
TESTSET: list[tuple[str, list[str], str | None, float | None]] = [
    ("有没有橙子推荐", ["柑橘类"], "橙子", None),
    ("我想买苹果", ["仁果核果"], "苹果", None),
    ("现在买什么桃子好吃", ["仁果核果"], "桃", None),
    ("柚子", ["柑橘类"], "柚", None),
    ("想吃榴莲", ["热带水果"], "榴莲", None),
    ("哪种芒果甜", ["热带水果"], "芒果", None),
    ("浆果都有什么", ["浆果类"], "莓", None),
    ("买点大米", ["大米"], "大米", None),
    ("东北的大米", ["大米"], "东北大米", None),
    ("煮粥用什么杂粮好", ["杂粮"], None, None),
    ("挂面有几种", ["面粉挂面"], "挂面", None),
    ("包饺子买什么面粉", ["面粉挂面"], "面粉", None),
    ("什么油比较健康", ["食用油"], "油", None),
    ("调料酱料来点", ["酱料"], "酱", None),
    ("做川菜要买什么椒", ["香辛料"], "辣椒", None),
    ("10元以内有什么蔬菜", ["叶菜类", "茄果类", "根茎类", "葱蒜类", "鲜菇"], None, 10.0),
    ("减肥低脂吃什么肉", ["禽类"], None, None),
    ("给小宝宝做辅食买什么", ["蛋类", "奶制品"], None, None),
    ("有什么泡水喝的", ["花草茶", "蜂蜜冲调"], None, None),
    ("送礼的茶叶哪种好", ["绿茶红茶", "乌龙普洱"], "茶", None),
    ("深山里的蜂蜜", ["蜂蜜冲调"], "蜂蜜", None),
    ("买些干香菇木耳", ["干制菌菇"], "香菇", None),
    ("山里的松子", ["山珍干货"], "松子", None),
    ("过年送小孩什么零食", ["蜜饯果干", "坚果炒货", "粗粮零食"], None, None),
    ("腊肠", ["腊味火腿"], "腊肠", None),
    ("阳澄湖大闸蟹", ["虾蟹"], "大闸蟹", None),
    ("买条大鱼", ["海鱼", "淡水鱼虾"], "鱼", None),
    ("蚝和扇贝", ["贝类"], "生蚝", None),
    ("坐月子的产妇买点什么好", ["滋补特产"], None, None),
    ("宁夏的枸杞", ["滋补特产"], "枸杞", None),
    ("山药养胃", ["根茎类"], "山药", None),
    ("冲藕粉喝", ["蜂蜜冲调"], "藕", None),
    ("红薯做的零食", ["粗粮零食", "根茎类"], "红薯", None),
    ("买点豆制品", ["豆制品"], "豆腐", None),
    ("下饭的咸菜泡菜", ["腌菜泡菜"], "榨菜", None),
    ("30元以内的水果", ["柑橘类", "仁果核果", "热带水果", "浆果类", "瓜果类"], None, 30.0),
    ("洪湖的莲藕", ["根茎类"], "莲藕", None),
    ("新鲜竹笋", ["竹笋"], "笋", None),
    ("鸡胸肉健身餐", ["禽类"], "鸡胸", None),
    ("夏天解暑的瓜", ["瓜果类"], None, None),
]


def load_catalog(session):
    """在售商品池：[{id, name, subtitle, price, sales, category}]（相关集与通道共用）"""
    from sqlalchemy import text

    rows = session.execute(
        text(
            "select p.id, p.name, p.subtitle, p.price, p.sales, c.name as cat "
            "from product p join product_category c on c.id = p.category_id "
            "where p.status = 1 and p.deleted = 0"
        )
    ).all()
    return [
        {"id": r[0], "name": r[1] or "", "subtitle": r[2] or "", "price": float(r[3]),
         "sales": int(r[4]), "category": r[5]}
        for r in rows
    ]


def lane_hot(catalog, query, keyword, max_price):
    pool = [p for p in catalog if max_price is None or p["price"] <= max_price]
    return sorted(pool, key=lambda p: -p["sales"])


def lane_keyword(catalog, query, keyword, max_price):
    pool = catalog
    if max_price is not None:
        pool = [p for p in pool if p["price"] <= max_price]
    if keyword:
        pool = [p for p in pool if keyword in p["name"] or keyword in p["subtitle"]]
    return sorted(pool, key=lambda p: -p["sales"])


def lane_vector(store, catalog, query, keyword, max_price):
    """真实语义召回（top 8）+ 线上同款 min-score 闸门；价格上限与 Java 二级一致：召回后按价过滤

    闸门必须与 recommend 节点一致，否则评测衡量的是"线上不会走的那条通道"。
    """
    from app.config import settings

    by_id = {p["id"]: p for p in catalog}
    hits = [
        h for h in store.search(query, top_k=8)
        if h["score"] >= settings.REC_SEARCH_MIN_SCORE
    ]
    out = [by_id[h["id"]] for h in hits if h["id"] in by_id]
    if max_price is not None:
        out = [p for p in out if p["price"] <= max_price]
    return out


def evaluate_lane(lane_name: str, per_query_ranks: list[list[dict]], items, catalog) -> dict:
    """per_query_ranks[i] 为该问句的排序候选（全量，评测取前 TOP_K_METRIC）"""
    by_id = {p["id"]: p for p in catalog}
    rows = []
    hit1 = hit5 = 0.0
    prec_sum = rr_sum = 0.0
    cat_hits = set()
    for (q, cats, kw, mp), ranked in zip(items, per_query_ranks):
        relevant = {p["id"] for p in catalog if p["category"] in cats
                    and (mp is None or p["price"] <= mp)}
        top = [p["id"] for p in ranked[:TOP_K_METRIC]]
        n_hit = sum(1 for pid in top if pid in relevant)
        hit1 += 1.0 if top and top[0] in relevant else 0.0
        hit5 += 1.0 if n_hit > 0 else 0.0
        prec_sum += n_hit / TOP_K_METRIC
        rr = 0.0
        for i, pid in enumerate(top):
            if pid in relevant:
                rr = 1.0 / (i + 1)
                break
        rr_sum += rr
        cat_hits.update(by_id[pid]["category"] for pid in top if pid in by_id)
        rows.append({
            "query": q, "categories": cats, "keyword": kw, "max_price": mp,
            "relevant_count": len(relevant),
            "top5": [{"id": pid, "name": by_id[pid]["name"], "category": by_id[pid]["category"]}
                     for pid in top],
            "hit": n_hit > 0,
            "precision_at_5": round(n_hit / TOP_K_METRIC, 4),
            "reciprocal_rank": round(rr, 4),
        })
    n = len(items)
    return {
        "channel": lane_name,
        "queries": n,
        "hit_at_1": round(hit1 / n, 4),
        "hit_at_5": round(hit5 / n, 4),
        "precision_at_5": round(prec_sum / n, 4),
        "mrr": round(rr_sum / n, 4),
        "category_coverage": len(cat_hits),
        "misses": [r["query"] for r in rows if not r["hit"]],
        "details": rows,
    }


def evaluate_personalization_replay(store, catalog, items):
    """个性化对照重放实验（模拟用户历史，不写库）：

    对每条问句取向量召回 top-8，找"语义相关但排在第 4~8 名"的候选作目标商品
    （即纯语义通道错过 top-5 的商品）；模拟一个近期连购 3 件该商品同类目的用户
    （偏好权重 1.0），同一份召回候选分别经 无偏好（基线）/ 有偏好（个性化）重排，
    对比目标商品进入 top-5 的比例与平均名次变化。

    与线上重排完全同函数（personalize.score_products，三条链路共用的唯一实现），
    差异仅在是否叠加历史偏好；口碑/新鲜度数据不在离线池里，两侧同为常数，不影响名次对比。
    """
    from app.config import settings
    from app.rag.personalize import score_products

    by_id = {p["id"]: p for p in catalog}
    # 与线上同口径：销量按全库在售最大销量归一，而非当前候选集内部最大值
    sales_max = max((p["sales"] for p in catalog), default=0)
    eligible = 0
    promoted = 0
    rank_deltas = []
    details = []
    for q, cats, kw, mp in items:
        # 与线上同款闸门：低于阈值的召回线上根本不会进重排，模拟时也要先滤掉
        hits = [
            h for h in store.search(q, top_k=8)
            if h["score"] >= settings.REC_SEARCH_MIN_SCORE
        ]
        relevant_ranked = [
            h for h in hits
            if h["id"] in by_id
            and by_id[h["id"]]["category"] in cats
            and (mp is None or by_id[h["id"]]["price"] <= mp)
        ]
        # 目标：相关性成立但排在第 4~8 名（基线通道进不了 top-5）
        targets = [h for h in relevant_ranked if relevant_ranked.index(h) >= 3]
        if not targets:
            continue
        target = targets[-1]  # 名次最差的相关商品——最难的正例
        eligible += 1
        tid = target["id"]
        tcat = by_id[tid]["category"]
        cand = [by_id[h["id"]] for h in hits if h["id"] in by_id]
        sem = {h["id"]: h["score"] for h in hits}

        cat_of = {p["id"]: p["category"] for p in cand}

        def rank_at(pref):
            ranked = score_products(
                cand, sem_by_id=sem, pref_by_cat=pref, cat_by_id=cat_of, sales_max=sales_max
            )
            return [i["id"] for i in ranked].index(tid) + 1

        r_base, r_pers = rank_at({}), rank_at({tcat: 1.0})
        rank_deltas.append(r_base - r_pers)
        hit_now = r_pers <= TOP_K_METRIC
        promoted += 1 if hit_now else 0
        details.append({
            "query": q, "target": {"id": tid, "name": by_id[tid]["name"], "category": tcat},
            "rank_baseline": r_base, "rank_personalized": r_pers,
            "entered_top5": hit_now,
            "alpha": settings.REC_PREF_ALPHA,
        })

    n = eligible
    if n == 0:
        return {"samples": 0, "note": "测试集中无'语义第4~8名相关候选'可作重放样本"}
    return {
        "samples": n,
        "nature": "机制验证（合成行为）：证明重排公式能把偏好类目商品推入 top-5，"
                  "不构成个性化效果评估——目标商品由合成历史自身类目决定，"
                  "被优化信号与判定标准同源，基线 0.0 是构造使然而非实测。"
                  "效果口径需 A/B 或线上点击数据，本系统未采集。",
        "method": "模拟同类目连购3件的用户历史；同公式仅切换是否叠加偏好项",
        "hit_at_5_baseline": 0.0,  # 构造使然：目标商品基线全部在 top-5 之外
        "hit_at_5_personalized": round(promoted / n, 4),
        "avg_rank_gain": round(sum(rank_deltas) / n, 2),
        "details": details,
    }


def main():
    from app.db import SessionLocal
    from app.main import get_product_store

    session = SessionLocal()
    try:
        catalog = load_catalog(session)
        store = get_product_store()
        if store.count() == 0:
            print("商品索引为空，先执行全量重建……")
            print("indexed:", store.rebuild(session))

        lanes = {
            "hot": lambda q, kw, mp: lane_hot(catalog, q, kw, mp),
            "keyword": lambda q, kw, mp: lane_keyword(catalog, q, kw, mp),
            "vector": lambda q, kw, mp: lane_vector(store, catalog, q, kw, mp),
        }
        results = []
        for name, fn in lanes.items():
            per_query = [fn(q, kw, mp) for q, cats, kw, mp in TESTSET]
            results.append(evaluate_lane(name, per_query, TESTSET, catalog))

        print_report(results)
        replay = evaluate_personalization_replay(store, catalog, TESTSET)
        print_replay(replay)
        RESULT_DIR.mkdir(parents=True, exist_ok=True)
        out = RESULT_DIR / "recommend_eval.json"
        out.write_text(json.dumps({
            **provenance(),
            "catalog_size": len(catalog),
            "testset": {"queries": len(TESTSET), "authoring": "人工为每条查询标注可接受类目集合（单一 gold 集合，"
                         "同类目下任意商品均计为命中；跨类目合理替代不计分）"},
            "results": results,
            "personalization_replay": replay,
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n结果已写入：{out}")
    finally:
        session.close()


def print_report(results):
    print("\n## 推荐召回评估（商品向量索引 vs 关键词 LIKE vs 热销榜）\n")
    print(f"| 通道 | 查询数 | Hit@1 | Hit@5 | P@5 | MRR | 类目覆盖 |")
    print("|---|---|---|---|---|---|---|")
    for r in results:
        print(f"| {r['channel']} | {r['queries']} | {r['hit_at_1']:.1%} | {r['hit_at_5']:.1%} "
              f"| {r['precision_at_5']:.1%} | {r['mrr']:.3f} | {r['category_coverage']} |")
    for r in results:
        if r["misses"]:
            print(f"\n{r['channel']} 未命中样本（{len(r['misses'])} 条）：")
            for q in r["misses"][:10]:
                print(f"  - {q}")


def print_replay(replay):
    print("\n## 个性化对照重放（模拟同类目连购历史：语义第 4~8 名的相关商品能否被拉进 top-5）\n")
    if replay.get("samples", 0) == 0:
        print(replay.get("note", "无样本"))
        return
    print(f"样本数 {replay['samples']}（{replay['method']}）")
    print(f"| 通道 | 目标商品进 top-5 | 平均名次提升 |")
    print(f"|---|---|---|")
    print(f"| 纯语义（无历史） | 0.0%（构造使然） | - |")
    print(f"| 语义 + 类目偏好重排 | {replay['hit_at_5_personalized']:.1%} | +{replay['avg_rank_gain']:.2f} 名 |")
    promoted = [d for d in replay["details"] if d["entered_top5"]][:5]
    if promoted:
        print("\n重排进位示例：")
        for d in promoted:
            print(f"  - 「{d['query']}」目标「{d['target']['name']}」 名次 {d['rank_baseline']} → {d['rank_personalized']}")


if __name__ == "__main__":
    main()
