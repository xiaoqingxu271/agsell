"""实验八：知识库重排（bge-reranker-base 交叉编码器）对 Hit@1 的增益与延迟

背景：量化实验三显示 platform_faq 的 Hit@3 是 98.6% 而 Hit@1 只有 90.3%——
正确条目几乎都在前三，融合排序却排不准第一名。交叉编码器（query 与文档拼接后
逐对过编码器）是修这个差距最稳的一招，代价是每次检索多一次本地 ONNX 推理。

实验设计：
- 候选：生产三路检索器 recall@20（与线上一致的闸门与融合，只是取更深）；
- 对照：融合分序（生产默认） vs 交叉编码重排 top-5；
- 指标：Hit@1 / Hit@3 / MRR + 重排延迟（ms/query，热身后）；
- 结论口径：Hit@1 提升显著且延迟可接受 → 生产打开 RERANK_ENABLED，否则只作实验存档。

用法（在 ai-service 目录下；本地开发请覆盖 DATABASE_URL 指向 127.0.0.1）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/eval_rerank.py

输出：控制台报告 + scripts/eval_results/rerank_eval.json
"""
import json
import statistics
import sys
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

from _provenance import provenance  # noqa: E402
from eval_rag import PARAPHRASES, channel_eval  # noqa: E402  复用主口径改写集与指标口径

RECALL_DEPTH = 20


def main() -> None:
    from app.config import settings
    from app.db import SessionLocal
    from app.rag.reranker import get_reranker
    from app.rag.retriever import Retriever
    from app.rag.vectorstore import VectorStore
    from app.repositories import faq_repo

    vs = VectorStore(path=settings.VECTOR_DB_PATH, collection="faq")
    retriever = Retriever(vector_store=vs, faq_repo=faq_repo, session_factory=SessionLocal)
    reranker = get_reranker()

    def rank_by_fusion(query: str) -> list[int]:
        """对照组：生产三路融合序，取前 5（KB_TOP_K 口径）"""
        hits = retriever.retrieve(query, top_k=RECALL_DEPTH)
        return [h["id"] for h in hits[:settings.KB_TOP_K]]

    def rank_by_rerank(query: str) -> list[int]:
        """实验组：同一条召回池 → 交叉编码重排 → 前 5"""
        hits = retriever.retrieve(query, top_k=RECALL_DEPTH)
        reranked = reranker.rerank(
            query, hits,
            text_of=lambda h: f"{h['question']}\n{h['answer']}",
            top_k=settings.KB_TOP_K,
        )
        return [h["id"] for h in reranked]

    t0 = time.time()
    fusion = channel_eval("fusion_top5", rank_by_fusion, PARAPHRASES)
    rerank = channel_eval("rerank_top5", rank_by_rerank, PARAPHRASES)
    eval_ms = (time.time() - t0) * 1000

    # 重排延迟：热身后逐查询计时（检索已热，只量 rerank 段）
    sample = [q for qs in PARAPHRASES.values() for q in qs][:24]
    latencies = []
    for q in sample:
        hits = retriever.retrieve(q, top_k=RECALL_DEPTH)
        t1 = time.time()
        reranker.rerank(q, hits, text_of=lambda h: f"{h['question']}\n{h['answer']}",
                        top_k=settings.KB_TOP_K)
        latencies.append((time.time() - t1) * 1000)

    print("\n## 实验八：知识库重排（bge-reranker-base，量化 ONNX，本地 CPU）")
    print("\nplatform_faq（72 条人工改写，论文主口径，recall@20 候选池）\n")
    print("| 召回与排序 | Hit@1 | Hit@3 | MRR |")
    print("|---|---|---|---|")
    for r in (fusion, rerank):
        print(f"| {r['channel']} | {r['hit_at_1']:.1%} | {r['hit_at_3']:.1%} | {r['mrr']:.3f} |")
    print(f"\n重排延迟（{len(latencies)} 次，{RECALL_DEPTH} 候选 → top5）："
          f"P50 {statistics.median(latencies):.0f} ms / "
          f"P95 {sorted(latencies)[int(len(latencies)*0.95)]:.0f} ms / "
          f"max {max(latencies):.0f} ms")

    result = {
        **provenance(),
        "model": "BAAI/bge-reranker-base (Xenova quantized ONNX)",
        "recall_depth": RECALL_DEPTH,
        "platform_faq": {"fusion": fusion, "rerank": rerank},
        "rerank_latency_ms": {
            "p50": round(statistics.median(latencies), 1),
            "p95": round(sorted(latencies)[int(len(latencies) * 0.95)], 1),
            "max": round(max(latencies), 1),
        },
    }
    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULT_DIR / "rerank_eval.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n结果已写入：{out}")
    _ = eval_ms  # 评测总耗时仅供参考，不入档


if __name__ == "__main__":
    main()
