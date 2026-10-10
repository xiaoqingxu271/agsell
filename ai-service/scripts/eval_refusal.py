"""拒答率评估（反幻觉闸门）：域外问题应拒答、域内问题不应误拒

对量化实验的补充：eval_rag 测"该答的答对没有"（命中率），本脚本测
"不该答的答了没有"（拒答率）——即 KB_SCORE_THRESHOLD 阈值闸门的效果。

指标：
- 域外拒答率：知识库不覆盖的问题被拒答（retrieve 返回空 → 确定性拒答话术）的比例，越高越好；
- 域内误拒率：知识库覆盖的同义改写被误拒的比例，越低越好；
- 域外 top-1 相似度分布：佐证阈值取值。

用法（在 ai-service 目录下；本地开发请覆盖 DATABASE_URL 指向 127.0.0.1）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/eval_refusal.py

若长时间无输出：bge 模型缓存丢失（fastembed 默认缓存位于系统 Temp，可能被磁盘清理删除），
重新拉取：HF_ENDPOINT=https://hf-mirror.com .venv/Scripts/python -c "from fastembed import TextEmbedding; TextEmbedding('BAAI/bge-small-zh-v1.5')"

输出：控制台报告 + scripts/eval_results/refusal_eval.json
"""
import json
import statistics
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from eval_rag import PARAPHRASES  # noqa: E402  复用同义改写集（域内样本）

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

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

    RESULT_DIR.mkdir(exist_ok=True)
    out_path = RESULT_DIR / "refusal_eval.json"
    out_path.write_text(
        json.dumps({
            "threshold": threshold,
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
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n结果已写入：{out_path}")


if __name__ == "__main__":
    main()
