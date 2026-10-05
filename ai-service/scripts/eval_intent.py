"""意图识别准确率评估

用法（在 ai-service 目录下）：
    .venv/Scripts/python scripts/eval_intent.py --mode keyword   # 纯关键词兜底通道
    .venv/Scripts/python scripts/eval_intent.py --mode llm       # 生产路径（LLM 分类，失败自动兜底）
    .venv/Scripts/python scripts/eval_intent.py --mode llm --workers 6

输出：控制台 Markdown 报告 + scripts/eval_results/intent_eval_<mode>.json
"""
import argparse
import json
import sys
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

TESTSET_PATH = PROJECT_ROOT / "scripts" / "eval_data" / "intent_testset.json"
RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"


def load_testset() -> list[dict]:
    with open(TESTSET_PATH, encoding="utf-8") as f:
        return json.load(f)["cases"]


def evaluate(predictions: list[tuple[str, str | None]], cases: list[dict]) -> dict:
    labels = sorted({c["intent"] for c in cases})
    confusion = Counter()
    per_intent = {lb: {"total": 0, "correct": 0} for lb in labels}
    correct = 0
    misclassified = []
    for case, (pred, _order_no) in zip(cases, predictions):
        truth = case["intent"]
        per_intent[truth]["total"] += 1
        confusion[(truth, pred)] += 1
        if pred == truth:
            correct += 1
            per_intent[truth]["correct"] += 1
        else:
            misclassified.append({"q": case["q"], "truth": truth, "pred": pred})

    total = len(cases)
    return {
        "total": total,
        "accuracy": round(correct / total, 4),
        "per_intent": {
            lb: {
                "total": v["total"],
                "correct": v["correct"],
                "recall": round(v["correct"] / v["total"], 4),
            }
            for lb, v in per_intent.items()
        },
        "confusion": [
            {"truth": t, "pred": p, "count": n} for (t, p), n in confusion.items() if t != p and n > 0
        ],
        "misclassified": misclassified,
    }


def print_report(mode: str, result: dict, latencies_ms: list[float], order_no_acc: float):
    print(f"\n## 意图识别评估报告（mode={mode}）\n")
    print(f"- 测试集规模：{result['total']} 条（{len(result['per_intent'])} 类意图）")
    print(f"- **总体准确率：{result['accuracy']:.1%}**")
    if latencies_ms:
        lat_sorted = sorted(latencies_ms)
        p95 = lat_sorted[int(len(lat_sorted) * 0.95) - 1]
        print(f"- 单条分类延迟：平均 {sum(latencies_ms) / len(latencies_ms):.0f} ms，P95 {p95:.0f} ms")
    if order_no_acc is not None:
        print(f"- 订单号抽取准确率（正则）：{order_no_acc:.1%}")
    print("\n| 意图 | 样本数 | 正确 | 召回率 |")
    print("|---|---|---|---|")
    for lb, v in result["per_intent"].items():
        print(f"| {lb} | {v['total']} | {v['correct']} | {v['recall']:.1%} |")
    if result["confusion"]:
        print("\n主要混淆（真实 → 误判）：")
        for c in sorted(result["confusion"], key=lambda x: -x["count"])[:10]:
            print(f"- {c['truth']} → {c['pred']}（{c['count']} 条）")
    if result["misclassified"]:
        print("\n误分类样本（最多展示 15 条）：")
        for m in result["misclassified"][:15]:
            print(f"- 「{m['q']}」 {m['truth']} → {m['pred']}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["keyword", "llm"], default="keyword")
    parser.add_argument("--workers", type=int, default=4, help="LLM 模式并发数（防免费额度限流）")
    args = parser.parse_args()

    cases = load_testset()

    if args.mode == "keyword":
        from app.agent.intent import _extract_order_no, _keyword_fallback

        t0 = time.perf_counter()
        predictions = [(_keyword_fallback(c["q"]), _extract_order_no(c["q"])) for c in cases]
        wall_ms = (time.perf_counter() - t0) * 1000
        latencies = [wall_ms / len(cases)] * len(cases)
    else:
        from app.agent.intent import classify_intent
        from app.config import settings
        from langchain_openai import ChatOpenAI

        llm = ChatOpenAI(
            model=settings.AGNES_MODEL,
            api_key=settings.AGNES_API_KEY,
            base_url=settings.AGNES_BASE_URL,
            temperature=settings.AGNES_TEMPERATURE,
            timeout=settings.LLM_TIMEOUT,
            max_retries=settings.LLM_MAX_RETRIES,
        )
        predictions: list[tuple[str, str | None]] = [None] * len(cases)  # type: ignore
        latencies: list[float] = [0.0] * len(cases)

        def run_one(i: int, q: str):
            t0 = time.perf_counter()
            predictions[i] = classify_intent(q, llm)
            latencies[i] = (time.perf_counter() - t0) * 1000

        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(run_one, i, c["q"]) for i, c in enumerate(cases)]
            for f in futures:
                f.result()

    # 订单号抽取与模式无关（正则通道）
    from app.agent.intent import _extract_order_no

    order_cases = [c for c in cases if "order_no" in c]
    order_hits = sum(1 for c in order_cases if _extract_order_no(c["q"]) == c["order_no"])
    order_no_acc = order_hits / len(order_cases) if order_cases else None

    result = evaluate(predictions, cases)
    RESULT_DIR.mkdir(exist_ok=True)
    out_path = RESULT_DIR / f"intent_eval_{args.mode}.json"
    out_path.write_text(
        json.dumps(
            {"mode": args.mode, "testset": str(TESTSET_PATH), **result, "order_no_accuracy": order_no_acc},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print_report(args.mode, result, latencies, order_no_acc)
    print(f"\n结果已写入：{out_path}")


if __name__ == "__main__":
    main()
