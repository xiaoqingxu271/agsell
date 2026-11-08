"""语义缓存实验（实验十二）：同义改写命中率 + 阈值曲线 + 延迟开销（纯本地，零 LLM 额度）

设计：与 RAG 命中率实验同源同难度的 platform_faq 人工改写集（36 条 FAQ × 2 条
同义改写 = 72 条查询，PARAPHRASES 直接复用 eval_rag.py 的标注）作为查询集；
36 条 FAQ 原问句作为缓存条目"预热"，然后逐条改写查询测：
  1) 阈值-命中率曲线（0.85~0.97）：改写问句能否映射回**正确**的原 FAQ（命中且
     命中到对的条目——映射错了就是答非所问，必须同 Hit@1 一样严格计分）；
  2) 生产阈值（SEMANTIC_CACHE_THRESHOLD=0.93）下的命中率与同义改写命中分布；
  3) 延迟：embed_query（未命中的新增开销）与 lookup（全表扫描）P50/P95，
     对照全链路 FAQ 首 token P50 9.6s（检索+重排+两次串行 LLM）。

用法（ai-service 目录下，需本地 MySQL）：
    .venv/Scripts/python scripts/eval_cache.py
输出：控制台 Markdown 报告 + scripts/eval_results/cache_eval.json（带溯源字段）
"""
import statistics
import sys
import tempfile
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import numpy as np  # noqa: E402

from _provenance import provenance  # noqa: E402
from eval_rag import PARAPHRASES, check_paraphrase_ids_still_valid, load_faqs_from_db  # noqa: E402

THRESHOLDS = [round(t, 2) for t in np.arange(0.85, 0.971, 0.01)]
PROD_THRESHOLD = 0.88   # 与 config.SEMANTIC_CACHE_THRESHOLD 同步（阈值-风险曲线选点）


def main() -> None:
    from fastembed import TextEmbedding

    from app import semantic_cache
    from app.config import settings

    faqs = load_faqs_from_db()
    problems = check_paraphrase_ids_still_valid(faqs)
    if problems:
        raise SystemExit("标注集 id 校验失败：\n" + "\n".join(problems))
    by_id = {f["id"]: f for f in faqs}

    model = TextEmbedding(settings.EMBEDDING_MODEL)

    def embed(text: str) -> np.ndarray:
        return semantic_cache.embed_query(model, text)

    # ---------- 预热：36 条原问句入临时缓存库（向量只算一次，复用） ----------
    db_path = str(Path(tempfile.mkdtemp()) / "cache_eval.db")
    faq_vecs = np.stack([embed(f["question"]) for f in faqs])  # (36, d)
    faq_norms = np.linalg.norm(faq_vecs, axis=1)
    for f, v in zip(faqs, faq_vecs):
        semantic_cache.store(db_path, f["question"], v, f["answer"], [])
    n_entries = semantic_cache.count(db_path)

    # ---------- 查询：72 条改写逐一算"最优映射 + 分数" ----------
    embed_ms: list[float] = []
    lookup_ms: list[float] = []
    results: list[dict] = []  # {fid, q, best_fid, best_score, correct}
    for fid, queries in PARAPHRASES.items():
        for q in queries:
            t0 = time.perf_counter()
            vec = embed(q)
            embed_ms.append((time.perf_counter() - t0) * 1000)

            # 与 lookup 同口径的相似度（矩阵点积，取最优映射）
            scores = (faq_vecs @ vec) / (faq_norms * np.linalg.norm(vec))
            best_idx = int(np.argmax(scores))
            best_fid = faqs[best_idx]["id"]

            # 真实 lookup（SQLite 全表扫描路径）单独计时
            t2 = time.perf_counter()
            semantic_cache.lookup(db_path, vec, threshold=PROD_THRESHOLD,
                                  ttl_days=settings.SEMANTIC_CACHE_TTL_DAYS)
            lookup_ms.append((time.perf_counter() - t2) * 1000)

            results.append({
                "fid": fid, "q": q, "best_fid": best_fid,
                "best_score": round(float(scores[best_idx]), 4),
                "correct": best_fid == fid,
            })

    # ---------- 对照组：原句重试（缓存收益的主要来源：同一问题被反复原样问出） ----------
    repeat_results: list[dict] = []
    for f, v in zip(faqs, faq_vecs):
        scores = (faq_vecs @ v) / (faq_norms * np.linalg.norm(v))
        best_idx = int(np.argmax(scores))
        repeat_results.append({"fid": f["id"], "best_fid": faqs[best_idx]["id"],
                               "best_score": round(float(scores[best_idx]), 4),
                               "correct": faqs[best_idx]["id"] == f["id"]})
    repeat_hit = sum(1 for r in repeat_results
                     if r["correct"] and r["best_score"] >= PROD_THRESHOLD) / len(repeat_results)

    # ---------- 阈值-命中率曲线 ----------
    curve = []
    for t in THRESHOLDS:
        eligible = [r for r in results if r["best_score"] >= t]
        # 命中缓存的条件：超阈值 且 映射到正确条目（映射错=答非所问，不计命中）
        hits = [r for r in eligible if r["correct"]]
        curve.append({
            "threshold": round(t, 2),
            "hit_rate": round(len(hits) / len(results), 4),
            "wrong_answer_rate": round((len(eligible) - len(hits)) / len(results), 4),
        })
    prod = next(c for c in curve if c["threshold"] == PROD_THRESHOLD)
    correct_scores = sorted(r["best_score"] for r in results if r["correct"])

    def pct(xs, p):
        xs = sorted(xs)
        return round(xs[min(int(len(xs) * p), len(xs) - 1)], 1) if xs else None

    payload = {
        **provenance(),
        "experiment": "semantic_cache",
        "queries": len(results),
        "cache_entries": n_entries,
        "prod_threshold": PROD_THRESHOLD,
        "hit_rate_at_prod": prod["hit_rate"],
        "wrong_answer_rate_at_prod": prod["wrong_answer_rate"],
        "repeat_hit_rate_at_prod": round(repeat_hit, 4),
        "correct_match_score_min": round(correct_scores[0], 4),
        "correct_match_score_p50": round(correct_scores[len(correct_scores) // 2], 4),
        "threshold_curve": curve,
        "embed_latency_ms": {"p50": pct(embed_ms, 0.5), "p95": pct(embed_ms, 0.95)},
        "lookup_latency_ms": {"p50": pct(lookup_ms, 0.5), "p95": pct(lookup_ms, 0.95)},
        "detail": results,
    }
    out = PROJECT_ROOT / "scripts" / "eval_results" / "cache_eval.json"
    out.write_text(__import__("json").dumps(payload, ensure_ascii=False, indent=2),
                   encoding="utf-8")

    # ---------- 控制台报告 ----------
    print("\n## 语义缓存实验报告（platform_faq 人工改写集）\n")
    print(f"- 缓存条目：{n_entries} 条 FAQ 原问句；查询：{len(results)} 条人工同义改写"
          f" + {len(repeat_results)} 条原句重试（对照组）")
    print(f"- **生产阈值 {PROD_THRESHOLD}：强改写命中率 {prod['hit_rate']:.1%}，"
          f"误命中（映射到错误条目）{prod['wrong_answer_rate']:.1%}；"
          f"原句重试命中率 {repeat_hit:.1%}**")
    print(f"- 正确映射的相似度分布：min {correct_scores[0]:.3f} / P50 "
          f"{correct_scores[len(correct_scores) // 2]:.3f}")
    print("\n### 阈值-命中率曲线\n")
    print("| 阈值 | 缓存命中率 | 误命中（答错条目） |")
    print("|---|---|---|")
    for c in curve:
        mark = "（生产值）" if c["threshold"] == PROD_THRESHOLD else ""
        print(f"| {c['threshold']:.2f} | {c['hit_rate']:.1%} {mark} | {c['wrong_answer_rate']:.1%} |")
    print(f"\n- 未命中路径新增开销：embed P50 {pct(embed_ms, 0.5)} ms / P95 {pct(embed_ms, 0.95)} ms")
    print(f"- 缓存查询开销：lookup P50 {pct(lookup_ms, 0.5)} ms / P95 {pct(lookup_ms, 0.95)} ms"
          f"（对照：全链路 FAQ 首 token P50 9.6 s，重排 P50 144.2 ms）")
    print(f"\n结果已写入：{out}")


if __name__ == "__main__":
    main()
