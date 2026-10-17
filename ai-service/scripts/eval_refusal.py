"""拒答率评估（两道反幻觉闸门）

实验五原只考察 **知识库闸门**（KB_SCORE_THRESHOLD）：检索为空 → 生成节点确定性拒答。
本轮补上 **导购闸门**（REC_SEARCH_MIN_SCORE）：意图被判为 recommend 时，整句进商品向量索引
召回，低于阈值的命中丢弃。两条链路语义完全不同——

- FAQ 分支：闸门失败 = 拒答（有兜底话术，用户不会收到错答案）；
- 导购分支：闸门失败 = 照样把"最像的农产品"塞进推荐语料，没有任何拒答出口。

而 REC_SEARCH_MIN_SCORE=0.35 当初是为**搜索框**定的（空结果可优雅退回全文检索），
被导购分支复用后，它面对的是"域外请求 vs 202 个农产品"的分布。本脚本量化这一复用的后果。

指标：
- 域外拒答率 / 域内误拒率（知识库闸门）；
- 导购闸门泄漏率：域外请求中"至少有一条商品命中过阈值"的比例，及被端上来的商品名（可读证据）；
- 阈值安全余量：域外 top-1 相似度最大值与阈值的差；知识库扩容会让整个分布上移，余量必须复测。

用法（在 ai-service 目录下；本地开发请覆盖 DATABASE_URL 指向 127.0.0.1）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/eval_refusal.py

若长时间无输出：bge 模型缓存丢失（fastembed 默认缓存位于系统 Temp，可能被磁盘清理删除），
重新拉取：HF_ENDPOINT=https://hf-mirror.com .venv/Scripts/python -c "from fastembed import TextEmbedding; TextEmbedding('BAAI/bge-small-zh-v1.5')"

输出：控制台报告 + scripts/eval_results/refusal_eval.json
"""
import json
import statistics
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from eval_rag import PARAPHRASES  # noqa: E402  复用同义改写集（域内样本）

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"


from _provenance import provenance  # noqa: E402  统一溯源字段

# 域外问题（知识库不覆盖）：电商客服语境之外的常见诉求
OUT_OF_DOMAIN_QUERIES = [
    "今天股市行情怎么样",
    "推荐一部好看的电影",
    "怎么备考研究生",
    "附近有什么好吃的火锅店",
    "帮我写一首诗",
    "世界杯决赛是哪两队",
    "如何办理护照",
    "推荐几本小说",
    "现在油价多少",
    "怎么学好英语",
    "周杰伦新歌什么时候发",
    "附近的健身房怎么收费",
]

# 导购分支专用探针：以"推荐/挑选"句式提出**完全不属于农产品域**的诉求。
# 这些问法会被意图路由判为 recommend，只能靠商品召回阈值挡住。
REC_OOD_QUERIES = [
    "推荐一部好看的电影",
    "有没有什么好用的手机壳",
    "帮我推荐几本考研的书",
    "想买台笔记本电脑，选哪个",
    "推荐个附近的健身房",
    "有没有好看的冬天外套",
    "帮我挑一支口红",
    "推荐几首好听的歌",
    "买什么牌子的好看电视剧",
    "推荐一双跑步鞋",
]

# 域内导购正例（应过闸门）：与 REC_OOD 同句式，构成对照
REC_IN_DOMAIN_QUERIES = [
    "有什么水果推荐",
    "推荐点适合减脂期吃的粗粮",
    "帮我挑个不太甜的茶叶",
    "有没有新上市的应季果子",
    "30 块钱左右能买到什么坚果",
    "送礼的茶叶哪种好",
    "想买点下饭的小菜",
    "过年走亲戚拎点什么体面",
    "有没有做辅食用的南瓜",
    "办公室当零食的东西推荐点",
]


def main() -> None:
    from app.config import settings
    from app.db import SessionLocal
    from app.rag.retriever import Retriever
    from app.rag.vectorstore import VectorStore
    from app.repositories import faq_repo

    threshold = settings.KB_SCORE_THRESHOLD
    print(f"拒答阈值 KB_SCORE_THRESHOLD = {threshold}")

    retriever = Retriever(
        VectorStore(path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL),
        faq_repo=faq_repo,
        session_factory=SessionLocal,
    )

    # 域内样本：全部同义改写（72 条）
    in_domain = [(fid, q) for fid, qs in PARAPHRASES.items() for q in qs]
    in_refused = []
    for fid, q in in_domain:
        if not retriever.retrieve(q):
            in_refused.append({"id": fid, "q": q})

    # 域外样本：query + 是否被拒 + top-1 向量相似度
    out_rows = []
    for q in OUT_OF_DOMAIN_QUERIES:
        hits = retriever.retrieve(q)
        top1 = retriever._vs.query(q, top_k=1)  # noqa: SLF001
        out_rows.append({
            "q": q,
            "refused": not hits,
            "returned": len(hits),
            "top1_vector_score": round(top1[0]["score"], 4) if top1 else None,
        })

    n_out = len(out_rows)
    out_refused = sum(1 for r in out_rows if r["refused"])
    out_rate = out_refused / n_out
    in_rate = len(in_refused) / len(in_domain)

    print("\n## 拒答率评估（反幻觉闸门）\n")
    print(f"| 样本 | 数量 | 拒答/误拒 | 比例 |")
    print(f"|---|---|---|---|")
    print(f"| 域外问题（应拒答） | {n_out} | {out_refused} | {out_rate:.1%} |")
    print(f"| 域内同义改写（不应拒） | {len(in_domain)} | {len(in_refused)} | {in_rate:.1%} |")

    scores = [r["top1_vector_score"] for r in out_rows if r["top1_vector_score"] is not None]
    if scores:
        print(f"\n域外 top-1 向量相似度：均值 {statistics.mean(scores):.3f}，"
              f"最大 {max(scores):.3f}，最小 {min(scores):.3f}")

    if in_refused:
        print(f"\n[误拒样本]（{len(in_refused)} 条，阈值为 {threshold}，可考虑下调或为该 FAQ 补充同义问法）：")
        for m in in_refused[:10]:
            print(f"  - [{m['id']}] {m['q']}")
    if not in_refused:
        print("\n域内零误拒：阈值未误伤任何知识库覆盖问题。")

    # ---------- 导购闸门（REC_SEARCH_MIN_SCORE）----------
    from app.rag.product_store import PRODUCT_COLLECTION

    min_score = settings.REC_SEARCH_MIN_SCORE
    product_vs = VectorStore(
        path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL, collection=PRODUCT_COLLECTION
    )
    print(f"\n## 导购闸门（商品召回阈值 {min_score}，索引 {product_vs.count()} 条）\n")

    def gate_probe(queries: list[str]) -> dict:
        rows = []
        for q in queries:
            hits = product_vs.query(q, top_k=settings.PRODUCT_TOP_K)
            passed = [h for h in hits if h["score"] >= min_score]
            rows.append({
                "q": q,
                "top1_score": round(hits[0]["score"], 4) if hits else None,
                "top1_name": hits[0]["question"] if hits else None,
                "passed": len(passed),
                "leaked": bool(passed),
            })
        leaked = sum(1 for r in rows if r["leaked"])
        return {
            "queries": len(rows),
            "leaked": leaked,
            "leak_rate": round(leaked / len(rows), 4) if rows else None,
            "top1_mean": round(statistics.mean([r["top1_score"] for r in rows if r["top1_score"]]), 4)
            if rows else None,
            "detail": rows,
        }

    rec_ood = gate_probe(REC_OOD_QUERIES)
    rec_in = gate_probe(REC_IN_DOMAIN_QUERIES)

    # 对话导购分支实际用的是独立闸门 REC_MIN_SCORE（0.47），这里按同一套样本再测一遍，
    # 让存档直接反映"线上现在生效的是哪个数"，而不是只留搜索框闸门下的曲线。
    chat_gate = settings.REC_MIN_SCORE

    def leak_rate(rows: list[dict], thr: float) -> float:
        return round(sum(1 for r in rows if (r["top1_score"] or 0) >= thr) / len(rows), 4)

    chat_ood = leak_rate(rec_ood["detail"], chat_gate)
    chat_in = leak_rate(rec_in["detail"], chat_gate)
    print(f"\n对话导购实际闸门 REC_MIN_SCORE = {chat_gate}："
          f"域外泄漏率 {chat_ood:.1%}（0.35 时 {rec_ood['leak_rate']:.1%}），"
          f"域内召回率 {chat_in:.1%}（0.35 时 {rec_in['leak_rate']:.1%}）")

    # 阈值-风险曲线：把"该把导购闸门调到多少"从主观判断变成可复现的选点依据
    curve = []
    for t in (0.35, 0.40, 0.45, 0.47, 0.50, 0.55):
        curve.append({
            "min_score": t,
            "ood_leak_rate": leak_rate(rec_ood["detail"], t),
            "in_domain_recall": leak_rate(rec_in["detail"], t),
        })
    print(f"\n| 导购闸门阈值 | 域外泄漏率 | 域内召回率 |")
    print(f"|---|---|---|")
    for c in curve:
        print(f"| {c['min_score']:.2f} | {c['ood_leak_rate']:.1%} | {c['in_domain_recall']:.1%} |")
    print(f"域外导购请求：{rec_ood['queries']} 条，闸门放行 {rec_ood['leaked']} 条"
          f"（泄漏率 {rec_ood['leak_rate']:.1%}），top-1 相似度均值 {rec_ood['top1_mean']}")
    print(f"域内导购请求（对照）：{rec_in['queries']} 条，闸门放行 {rec_in['leaked']} 条"
          f"（召回率 {rec_in['leak_rate']:.1%}），top-1 相似度均值 {rec_in['top1_mean']}")
    print("\n被放行的域外请求，实际端上桌的商品：")
    for r in rec_ood["detail"]:
        if r["leaked"]:
            print(f"  - 「{r['q']}」→ top1「{r['top1_name']}」{r['top1_score']}，过阈值 {r['passed']} 条")
    missed = [r["q"] for r in rec_in["detail"] if not r["leaked"]]
    if missed:
        print(f"\n域内被误挡（导购链路会退回热销榜）：{missed}")

    RESULT_DIR.mkdir(exist_ok=True)
    out_path = RESULT_DIR / "refusal_eval.json"
    out_path.write_text(
        json.dumps({
            **provenance(),
            "kb_gate": {
                "out_of_domain_top1_mean": round(statistics.mean(scores), 4) if scores else None,
                "out_of_domain_top1_max": round(max(scores), 4) if scores else None,
            },
            "threshold": threshold,
            "threshold_margin": round(threshold - max(scores), 4) if scores else None,
            "out_of_domain": {
                "queries": n_out,
                "refused": out_refused,
                "refusal_rate": round(out_rate, 4),
                "detail": out_rows,
            },
            "in_domain": {
                "queries": len(in_domain),
                "false_refused": len(in_refused),
                "false_refusal_rate": round(in_rate, 4),
                "detail": in_refused,
            },
            "rec_gate": {
                "search_lane_min_score": min_score,
                "chat_lane_min_score": chat_gate,
                "chat_lane_ood_leak_rate": chat_ood,
                "chat_lane_in_domain_recall": chat_in,
                "threshold_curve": curve,
                "out_of_domain": rec_ood,
                "in_domain_control": rec_in,
            },
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n结果已写入：{out_path}")


if __name__ == "__main__":
    main()
