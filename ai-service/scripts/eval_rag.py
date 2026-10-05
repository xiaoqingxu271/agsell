"""RAG 检索命中率评估：向量单路 vs 关键词单路 vs 双路合并（生产路径）

对知识库中每条 FAQ 构造 2 条同义改写问句（共 72 条查询），
分别测 hit@1 / hit@3 / MRR，另附 5 条域外问题验证相似度可分性。

用法（在 ai-service 目录下；本地开发请覆盖 DATABASE_URL 指向 127.0.0.1）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/eval_rag.py

输出：控制台报告 + scripts/eval_results/rag_eval.json
"""
import json
import statistics
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

# 每条 FAQ 的同义改写（下标 = ai_faq.id，生产库共 36 条）
PARAPHRASES: dict[int, list[str]] = {
    1: ["这个平台是干嘛用的", "介绍一下你们平台是做什么的"],
    2: ["人工客服电话多少", "怎么找到真人服务"],
    3: ["客服几点下班", "什么时间段有人工在线"],
    4: ["小程序登录方法", "进不去小程序怎么登陆"],
    5: ["登录会不会过期", "多久需要重新登录一次"],
    6: ["同一个商品有几种规格", "规格型号在哪个地方选"],
    7: ["没货了怎么办", "缺货的商品还能买到吗"],
    8: ["在哪里能看到商品的产地", "商品产地信息从哪里查"],
    9: ["不想要了能退吗", "退换货政策是什么"],
    10: ["加购物车的操作", "购物车怎么添加东西"],
    11: ["提交订单要填哪些信息", "下单需要准备什么资料"],
    12: ["付款有没有时限", "订单多久不付款会自动失效"],
    13: ["可以用什么付款", "有哪些付款渠道"],
    14: ["不想买了怎么取消", "订单取消的操作方法"],
    15: ["订单状态分别是什么意思", "订单有几种状态"],
    16: ["一般多长时间发货", "付款后几天内会发货"],
    17: ["在哪里查订单和物流", "怎么追踪我的快递"],
    18: ["收货确认在哪操作", "收到货之后怎么点确认"],
    19: ["邮费贵不贵", "运费的收费标准是什么"],
    20: ["售后在哪里申请", "申请售后的流程是什么"],
    21: ["售后都包含哪几种", "售后有哪些种类可以选"],
    22: ["售后要处理多长时间", "提交售后申请多久有结果"],
    23: ["水果烂了能赔吗", "收到的东西有质量问题怎么办"],
    24: ["售后有什么要求", "什么情况可以申请售后"],
    25: ["售后单有哪几种状态", "售后状态分别是什么含义"],
    26: ["评价在哪里发", "买了东西怎么写评价"],
    27: ["评价写错了能改吗", "评完之后还能修改吗"],
    28: ["溯源码怎么扫", "二维码溯源的使用方法"],
    29: ["什么商品可以查溯源", "是不是所有商品都有溯源"],
    30: ["怎么抢秒杀", "秒杀的参与方式"],
    31: ["秒杀价有什么不一样", "秒杀商品有什么特别之处"],
    32: ["总价是怎么算出来的", "订单价格包含哪些部分"],
    33: ["收货地址填错了怎么改", "修改地址在哪里操作"],
    34: ["怎么找以前买过的订单", "历史订单在哪里翻"],
    35: ["小程序首页都能干什么", "首页上有哪些板块"],
    36: ["搜东西的方法", "怎么查找我想要的商品"],
}

# 域外问题（知识库不覆盖，用于观察 top-1 相似度分布，论证拒答阈值）
NEGATIVE_QUERIES = [
    "今天股市行情怎么样",
    "推荐一部好看的电影",
    "怎么备考研究生",
    "附近有什么好吃的火锅店",
    "帮我写一首诗",
]


def load_faqs_from_db() -> list[dict]:
    from app.db import SessionLocal
    from app.repositories import faq_repo

    with SessionLocal() as session:
        rows = faq_repo.list_enabled(session)
        return [
            {"id": f.id, "question": f.question, "answer": f.answer, "keywords": f.keywords or ""}
            for f in rows
        ]


def keyword_channel(query: str, faqs: list[dict]) -> list[dict]:
    """复刻 Retriever 中关键词通道的打分：min(命中数,3)*0.5"""
    scored = []
    for f in faqs:
        matched = [kw for kw in (x.strip() for x in f["keywords"].split(",")) if kw and kw in query]
        if matched:
            scored.append({"id": f["id"], "score": min(len(matched), 3) * 0.5})
    scored.sort(key=lambda x: -x["score"])
    return scored


def rank_metrics(ranked_ids: list[int], expected: int, k: int = 3) -> dict:
    hit1 = 1 if ranked_ids[:1] == [expected] else 0
    hitk = 1 if expected in ranked_ids[:k] else 0
    mrr = 0.0
    for rank, fid in enumerate(ranked_ids, start=1):
        if fid == expected:
            mrr = 1.0 / rank
            break
    return {"hit1": hit1, "hit3": hitk, "mrr": mrr}


def channel_eval(name: str, rank_fn) -> dict:
    rows = []
    for fid, questions in PARAPHRASES.items():
        for q in questions:
            ranked = rank_fn(q)
            m = rank_metrics(ranked, fid)
            rows.append({"id": fid, "q": q, **m})
    n = len(rows)
    return {
        "channel": name,
        "queries": n,
        "hit_at_1": round(sum(r["hit1"] for r in rows) / n, 4),
        "hit_at_3": round(sum(r["hit3"] for r in rows) / n, 4),
        "mrr": round(sum(r["mrr"] for r in rows) / n, 4),
        "misses": [
            {"id": r["id"], "q": r["q"]} for r in rows if not r["hit3"]
        ],
    }


def main():
    from app.config import settings
    from app.rag.retriever import Retriever
    from app.rag.vectorstore import VectorStore

    faqs = load_faqs_from_db()
    by_id = {f["id"]: f for f in faqs}
    print(f"知识库 FAQ 数量：{len(faqs)}，改写查询：{sum(len(v) for v in PARAPHRASES.values())} 条")

    vector_store = VectorStore(path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL)
    print(f"向量索引条数：{vector_store.count()}")

    from app.db import SessionLocal
    from app.repositories import faq_repo

    retriever = Retriever(vector_store, faq_repo=faq_repo, session_factory=SessionLocal)

    def vector_ids(q: str) -> list[int]:
        return [h["id"] for h in vector_store.query(q, top_k=5)]

    def keyword_ids(q: str) -> list[int]:
        return [h["id"] for h in keyword_channel(q, faqs)]

    def merged_ids(q: str) -> list[int]:
        return [h["id"] for h in retriever.retrieve(q, top_k=5)]

    results = [
        channel_eval("vector_only", vector_ids),
        channel_eval("keyword_only", keyword_ids),
        channel_eval("merged_dual_channel", merged_ids),
    ]

    print("\n## RAG 检索命中率评估\n")
    print("| 召回通道 | 查询数 | Hit@1 | Hit@3 | MRR |")
    print("|---|---|---|---|---|")
    for r in results:
        print(f"| {r['channel']} | {r['queries']} | {r['hit_at_1']:.1%} | {r['hit_at_3']:.1%} | {r['mrr']:.3f} |")

    # 域外相似度可分性
    neg_scores = []
    pos_scores = []
    for q in NEGATIVE_QUERIES:
        hits = vector_store.query(q, top_k=1)
        if hits:
            neg_scores.append(hits[0]["score"])
    for qs in PARAPHRASES.values():
        hits = vector_store.query(qs[0], top_k=1)
        if hits:
            pos_scores.append(hits[0]["score"])
    if neg_scores:
        print(
            f"\n域内查询 top-1 相似度均值：{statistics.mean(pos_scores):.3f}；"
            f"域外查询 top-1 相似度均值：{statistics.mean(neg_scores):.3f}"
        )
        print("→ 两者分布可分，可据此设定拒答阈值（知识库不覆盖时引导转人工/重新提问）。")

    for r in results:
        if r["misses"]:
            print(f"\n[{r['channel']}] 未命中样本（{len(r['misses'])} 条，最多展示 10 条）：")
            for m in r["misses"][:10]:
                print(f"  - [{m['id']}] {m['q']} → 期望命中「{by_id[m['id']]['question']}」")

    RESULT_DIR.mkdir(exist_ok=True)
    out_path = RESULT_DIR / "rag_eval.json"
    payload = {"faq_count": len(faqs), "channels": results}
    if neg_scores:
        payload["separability"] = {
            "in_domain_top1_mean": round(statistics.mean(pos_scores), 4),
            "out_domain_top1_mean": round(statistics.mean(neg_scores), 4),
        }
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n结果已写入：{out_path}")


if __name__ == "__main__":
    main()
