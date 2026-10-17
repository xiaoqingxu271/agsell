"""意图识别准确率评估（三通道消融 + hold-out 测试集）

通道口径（三条互不重叠，用于回答「规则够用为什么要大模型」）：
  keyword   —— 纯规则通道：_hard_rule（高置信前置）or _keyword_fallback，零 LLM 调用；
  llm_only  —— 纯大模型通道：直接按 _INTENT_PROMPT 分类，**不做规则前置、不做关键词兜底**，
               LLM 调用失败或输出非法标签即计为错误（衡量模型独立能力下限）；
  llm       —— 生产路径 classify_intent：规则前置 → LLM → 关键词兜底（三者合成的线上真实表现）。

测试集：
  --testset holdout  → scripts/eval_data/intent_testset_holdout.json（2026-10-17 新标，
                       未参与规则调参，是论文主口径）
  --testset tuned    → scripts/eval_data/intent_testset.json（原始 103 题，已用于 _hard_rule /
                       _KEYWORD_RULES 调试，存在数据泄漏，只作回归参考，不作通道横向比较）
  也可直接传 JSON 路径。

用法（在 ai-service 目录下）：
    .venv/Scripts/python scripts/eval_intent.py --testset holdout --mode keyword
    .venv/Scripts/python scripts/eval_intent.py --testset holdout --mode llm_only --workers 1
    .venv/Scripts/python scripts/eval_intent.py --testset holdout --mode llm --workers 1
    # 免费额度按窗口配额，全集常跑到半程被 429 打断时，可只测固定种子的可复现子集：
    .venv/Scripts/python scripts/eval_intent.py --testset holdout --mode ll --limit 45 --workers 1

输出：控制台 Markdown 报告 + scripts/eval_results/intent_eval_<mode>[_<testset>][_n<条数>].json
子集结果自动带 _n<条数> 后缀，不会覆盖全集存档；JSON 内 sampled / sample_of_total / sample_seed
声明口径。每条 LLM 通道结果还附 rule_channel_reference（同一子集上的规则通道准确率，不花额度），
保证「规则 vs 大模型」永远是同日同样本的严格对比。
JSON 内含 measured_at 与 git_commit，确保报告正文与实验存档同源可追溯。
"""
import argparse
import json
import subprocess
import sys
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"
TESTSETS = {
    "holdout": PROJECT_ROOT / "scripts" / "eval_data" / "intent_testset_holdout.json",
    "tuned": PROJECT_ROOT / "scripts" / "eval_data" / "intent_testset.json",
}
DEFAULT_TESTSET = "tuned"


def load_testset(spec: str) -> tuple[list[dict], str]:
    """--testset 接受别名或显式路径；返回 (cases, 用于结果文件命名的标签)"""
    path = TESTSETS.get(spec, Path(spec))
    if not path.is_absolute():
        path = (PROJECT_ROOT / "scripts" / "eval_data" / spec) if not path.exists() else path
    if not path.exists():
        raise SystemExit(f"测试集不存在：{path}")
    with open(path, encoding="utf-8") as f:
        cases = json.load(f)["cases"]
    tag = "" if path.stem == "intent_testset" else path.stem.replace("intent_testset_", "")
    return cases, tag


def rule_predict(q: str) -> str:
    """规则通道预测：与生产 classify_intent 的规则部分一致
    （先高置信前置 human/纯寒暄，再关键词兜底）。"""
    from app.agent.intent import _hard_rule, _keyword_fallback

    return _hard_rule(q) or _keyword_fallback(q)


def sample_cases(cases: list[dict], limit: int, seed: int) -> list[dict]:
    """固定随机种子抽样：免费档 LLM 额度按窗口配额，109 条串行常跑到半程被 429 打断。
    允许只测一个**可复现的子集**，但必须在产物里声明是抽样——绝不把子集数字当全集报。"""
    if not limit or limit >= len(cases):
        return cases
    import random

    rnd = random.Random(seed)
    picked = sorted(rnd.sample(range(len(cases)), limit), key=lambda i: i)
    return [cases[i] for i in picked]


from _provenance import provenance  # noqa: E402  统一溯源字段


class CountingLLM:
    """包一层真实 LLM：统计调用数与失败数，并对 429 做长退避重试。

    为什么必须做这件事：Agnes 免费额度被打满时，langchain 抛 429 → 生产路径
    classify_intent 会**静默回落关键词通道**。评测脚本看到的只是"这条判错了"，
    于是把「额度故障」当成「模型能力」写进论文（原实验三 §3.3 观测到的"4 并发时
    准确率降到 76%"正是这个现象）。现在：
    - 失败计数与 429 计数入 JSON，valid 标记随之置假，脚本以非零码退出；
    - 429 先按 --retry-wait 长退避重试（免费额度是按窗口恢复的，等比放弃有效）。
    """

    def __init__(self, inner, retry_wait: float = 20.0, retries: int = 3):
        self._inner = inner
        self._retry_wait = retry_wait
        self._retries = retries
        self.calls = 0
        self.errors = 0
        self.rate_limited = 0

    def invoke(self, messages, **kwargs):
        self.calls += 1
        last = None
        for attempt in range(self._retries + 1):
            try:
                return self._inner.invoke(messages, **kwargs)
            except Exception as e:  # noqa: BLE001  分类计数后原样抛出
                last = e
                text = f"{type(e).__name__} {e}"
                if "429" in text or "RateLimit" in type(e).__name__:
                    self.rate_limited += 1
                    if attempt < self._retries:
                        time.sleep(self._retry_wait * (attempt + 1))
                        continue
                break
        self.errors += 1
        raise last


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
            misclassified.append({"q": case["q"], "truth": truth, "pred": pred,
                                  **({"feature": case["feature"]} if case.get("feature") else {})})

    total = len(cases)
    result = {
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

    # 分特征交叉：lexical（词面含规则词）vs semantic（无词面线索，只能靠语义）
    # 总体单一数字会掩盖「规则在其擅长的子集上近满分、在语义子集上崩掉」的结构差异
    feats = sorted({c["feature"] for c in cases if c.get("feature")})
    if feats:
        by_feature = {}
        for ft in feats:
            idx = [i for i, c in enumerate(cases) if c.get("feature") == ft]
            hit = sum(1 for i in idx if predictions[i][0] == cases[i]["intent"])
            by_feature[ft] = {
                "total": len(idx),
                "correct": hit,
                "accuracy": round(hit / len(idx), 4) if idx else None,
            }
        result["by_feature"] = by_feature
    return result


def print_report(mode: str, testset_tag: str, result: dict, latencies_ms: list[float],
                 order_no_acc: float, llm_calls: int, sampled_note: str = "",
                 rule_ref: dict | None = None):
    name = f"{mode}（{testset_tag or 'tuned'}）"
    print(f"\n## 意图识别评估报告 —— mode={name}\n")
    print(f"- 样本数：{result['total']} 条，{len(result['per_intent'])} 类意图")
    if sampled_note:
        print(f"- ⚠ {sampled_note}")
    print(f"- **总体准确率：{result['accuracy']:.1%}**")
    if rule_ref is not None:
        print(f"- 同一子集上的规则通道参照：**{rule_ref['accuracy']:.1%}**"
              f"（无额度依赖，同日同样本直接可比）")
    if latencies_ms:
        lat_sorted = sorted(latencies_ms)
        p95 = lat_sorted[int(len(lat_sorted) * 0.95) - 1]
        print(f"- 单条分类延迟：平均 {sum(latencies_ms) / len(latencies_ms):.0f} ms，P95 {p95:.0f} ms")
    if llm_calls:
        print(f"- LLM 调用次数：{llm_calls} / {result['total']} 条（其余由规则前置短路）")
    if order_no_acc is not None:
        print(f"- 订单号抽取准确率（正则）：{order_no_acc:.1%}")
    if result.get("by_feature"):
        print("\n### 分特征（词面线索有无）\n")
        print("| 子集 | 样本数 | 正确 | 准确率 |")
        print("|---|---|---|---|")
        for ft, v in result["by_feature"].items():
            print(f"| {ft} | {v['total']} | {v['correct']} | {v['accuracy']:.1%} |")
        if rule_ref and rule_ref.get("by_feature"):
            print("\n同一子集上的规则通道参照：")
            for ft, v in rule_ref["by_feature"].items():
                print(f"- {ft}：{v['accuracy']:.1%}（{v['correct']}/{v['total']}）")
    print("\n### 分意图\n")
    print("| 意图 | 样本数 | 正确 | 召回率 |")
    print("|---|---|---|---|")
    for lb, v in result["per_intent"].items():
        print(f"| {lb} | {v['total']} | {v['correct']} | {v['recall']:.1%} |")
    if result["confusion"]:
        print("\n主要混淆（真实 → 误判）：")
        for c in sorted(result["confusion"], key=lambda x: -x["count"])[:10]:
            print(f"- {c['truth']} → {c['pred']}（{c['count']} 条）")
    if result["misclassified"]:
        print("\n误分类样本（最多展示 25 条）：")
        for m in result["misclassified"][:25]:
            ft = f" [{m['feature']}]" if m.get("feature") else ""
            print(f"- 「{m['q']}」 {m['truth']} → {m['pred']}{ft}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["keyword", "llm", "llm_only"], default="keyword")
    parser.add_argument("--testset", default=DEFAULT_TESTSET,
                        help="holdout / tuned / 直接给 JSON 路径")
    parser.add_argument("--workers", type=int, default=4, help="LLM 模式并发数（防免费额度限流）")
    parser.add_argument("--retry-wait", type=float, default=20.0,
                        help="429 退避基数（秒），第 n 次重试等待 n×基数")
    parser.add_argument("--retries", type=int, default=3, help="429 长退避重试次数")
    parser.add_argument("--limit", type=int, default=0,
                        help="只测固定种子抽样的前 N 条（免费额度受窗口配额限制时用；产物会标 sampled）")
    parser.add_argument("--seed", type=int, default=20261017, help="抽样随机种子（可复现）")
    args = parser.parse_args()

    all_cases, tag = load_testset(args.testset)
    cases = sample_cases(all_cases, args.limit, args.seed)
    sampled_note = (
        f"本次为 {len(all_cases)} 条全集中的 {len(cases)} 条随机抽样"
        f"（seed={args.seed}，可复现）——受免费额度窗口配额限制未跑全集，引用时必须写明是子集"
        if len(cases) < len(all_cases) else ""
    )
    llm_calls = 0
    llm_errors = 0
    rate_limited = 0
    rule_ref: dict | None = None
    error_samples: list[str] = []

    if args.mode == "keyword":
        from app.agent.intent import _extract_order_no

        t0 = time.perf_counter()
        predictions = [(rule_predict(c["q"]), _extract_order_no(c["q"])) for c in cases]
        wall_ms = (time.perf_counter() - t0) * 1000
        latencies = [wall_ms / len(cases)] * len(cases)
    else:
        import re

        from app.agent.intent import _INTENT_PROMPT, INTENT_LABELS, _extract_order_no, classify_intent
        from app.config import settings
        from langchain_core.messages import HumanMessage, SystemMessage
        from langchain_openai import ChatOpenAI

        llm = CountingLLM(
            ChatOpenAI(
                model=settings.AGNES_MODEL,
                api_key=settings.AGNES_API_KEY,
                base_url=settings.AGNES_BASE_URL,
                temperature=settings.AGNES_TEMPERATURE,
                timeout=settings.LLM_TIMEOUT,
                max_retries=settings.LLM_MAX_RETRIES,
            ),
            retry_wait=args.retry_wait,
            retries=args.retries,
        )

        error_samples: list[str] = []

        def llm_only_predict(q: str) -> tuple[str, str | None]:
            """纯大模型通道：不前置规则、不兜底。失败/非法标签计为 __error__。"""
            try:
                resp = llm.invoke([SystemMessage(content=_INTENT_PROMPT), HumanMessage(content=q)])
                text = (resp.content or "").strip()
                text = re.sub(r"^```(?:json)?|```$", "", text, flags=re.MULTILINE).strip()
                data = json.loads(text)
                intent = str(data.get("intent", "")).strip().lower()
                if intent in INTENT_LABELS:
                    return intent, data.get("order_no") or _extract_order_no(q)
            except Exception as e:  # noqa: BLE001
                if len(error_samples) < 3:
                    error_samples.append(f"{type(e).__name__}: {str(e)[:160]}")
            return "__error__", None

        predict = llm_only_predict if args.mode == "llm_only" else (
            lambda q: classify_intent(q, llm)
        )

        predictions: list[tuple[str, str | None]] = [None] * len(cases)  # type: ignore
        latencies: list[float] = [0.0] * len(cases)

        def run_one(i: int, q: str):
            t0 = time.perf_counter()
            predictions[i] = predict(q)
            latencies[i] = (time.perf_counter() - t0) * 1000

        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(run_one, i, c["q"]) for i, c in enumerate(cases)]
            for f in futures:
                f.result()

        llm_calls = llm.calls
        llm_errors = llm.errors
        rate_limited = llm.rate_limited
        # 同一子集上的规则通道参照：不花额度、同日同样本，保证「规则 vs 模型」是严格可比的
        rule_ref = evaluate([(rule_predict(c["q"]), _extract_order_no(c["q"])) for c in cases], cases)

    from app.agent.intent import _extract_order_no

    order_cases = [c for c in cases if "order_no" in c]
    order_hits = sum(1 for c in order_cases if _extract_order_no(c["q"]) == c["order_no"])
    order_no_acc = order_hits / len(order_cases) if order_cases else None

    result = evaluate(predictions, cases)
    # 基础设施故障 ≠ 模型能力：只要有一条 LLM 调用最终失败，本次测量就不作数
    valid = llm_errors == 0
    if args.mode == "keyword":
        valid = True

    RESULT_DIR.mkdir(exist_ok=True)
    # 子集结果必须与全集结果分文件存放，否则一次抽样会覆盖掉全集存档
    suffix = f"_n{len(cases)}" if len(cases) < len(all_cases) else ""
    out_path = RESULT_DIR / f"intent_eval_{args.mode}{'_' + tag if tag else ''}{suffix}.json"
    if not valid:
        out_path = out_path.with_name(out_path.stem + f".{datetime.now():%Y%m%dT%H%M%S}.INVALID")
    payload = {
        "mode": args.mode,
        "testset": str(TESTSETS.get(args.testset, args.testset)),
        "testset_kind": tag or "tuned",
            **provenance(),
        "valid": valid,
        "sampled": bool(sampled_note),
        "sample_of_total": f"{len(cases)}/{len(all_cases)}",
        "sample_seed": args.seed,
        "llm_calls": llm_calls,
        "llm_errors": llm_errors,
        "rate_limited_calls": rate_limited,
        **result,
        "order_no_accuracy": order_no_acc,
    }
    if sampled_note:
        payload["sampling_note"] = sampled_note
    if rule_ref is not None:
        payload["rule_channel_reference"] = {
            "accuracy": rule_ref["accuracy"],
            "per_intent": rule_ref["per_intent"],
            "by_feature": rule_ref.get("by_feature"),
        }
    if error_samples:
        payload["first_errors"] = error_samples
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print_report(args.mode, tag, result, latencies, order_no_acc, llm_calls,
                 sampled_note=sampled_note, rule_ref=rule_ref)
    print(f"\n结果已写入：{out_path}")
    if not valid:
        print(
            f"\n⚠ 本次测量无效：{llm_errors} 条 LLM 调用失败（其中 429 限流 {rate_limited} 次），"
            f"结果文件已改名加 .INVALID 后缀，不得作为论文数据。\n"
            f"   免费额度按窗口恢复，请稍后重试或加大 --retry-wait / 降为 --workers 1。"
        )
        if error_samples:
            print("   首个错误：" + error_samples[0])
        sys.exit(1)


if __name__ == "__main__":
    main()
