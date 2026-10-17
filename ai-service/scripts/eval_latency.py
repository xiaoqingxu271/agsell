"""AI 客服响应性能测量：SSE 流式链路 TTFB / 取证阶段 / 整轮耗时

前置：ai-service 已启动（uvicorn app.main:app --port 8000），Java 主系统在 8080
（导购/订单类问题要回调 Java，缺它这些分支会走故障降级，延迟数字就不可比）。
用法：.venv/Scripts/python scripts/eval_latency.py [--rounds 1]

输出：控制台报告 + scripts/eval_results/latency_eval.json
⚠ 智能体改造后 status 事件不再带业务意图标签，而是带本轮真实调用的工具（tools）；
   报告里按"取证工具组合"分组，直接暴露一句多诉求要花几次调用。
"""
import argparse
import json
import statistics
import subprocess
import sys
import time
import uuid
from pathlib import Path

import requests

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"


from _provenance import provenance  # noqa: E402  统一溯源字段

# 覆盖四类链路（改造后每条都是"规划 → 执行 → 作答"，差别只在调了几个工具）：
#   smalltalk 规则前置直出（1 次 LLM）；faq / recommend 各 2 次（规划 + 作答）；
#   multi_intent 一句两个诉求：旧架构只能答一半，现在仍只 2 次 LLM（工具并行）
QUESTIONS = [
    ("smalltalk", "你好"),
    ("smalltalk", "在吗"),
    ("smalltalk", "谢谢你"),
    ("smalltalk", "再见"),
    ("faq", "运费怎么算"),
    ("faq", "支持哪些支付方式"),
    ("faq", "怎么申请售后"),
    ("faq", "溯源二维码怎么用"),
    ("faq", "秒杀活动怎么参加"),
    ("faq", "怎么修改收货地址"),
    ("recommend", "有什么水果推荐"),
    ("recommend", "50 元以内有什么好物"),
    ("recommend", "哪种茶叶比较好"),
    ("recommend", "帮我挑个送礼的"),
    ("multi_intent", "怎么申请退款？另外有没有 50 元以内的橙子"),
    ("multi_intent", "运费怎么算，顺便看看有什么坚果"),
    ("multi_intent", "我想查订单，另外秒杀活动怎么参加"),
]


def measure_one(base_url: str, headers: dict, question: str) -> dict:
    t0 = time.perf_counter()
    t_first_status = t_first_token = t_done = t_evidence = None
    intent = None
    tools: list[str] = []
    items = 0
    reply_chars = 0

    resp = requests.post(
        f"{base_url}/v1/chat/stream",
        headers=headers,
        json={"sessionId": f"eval-{uuid.uuid4().hex[:12]}", "message": question, "userId": None},
        stream=True,
        timeout=120,
    )
    resp.raise_for_status()
    event = None
    for line in resp.iter_lines(decode_unicode=True):
        now = time.perf_counter()
        if not line:
            continue
        if line.startswith("event:"):
            event = line.split(":", 1)[1].strip()
        elif line.startswith("data:"):
            data = line.split(":", 1)[1].strip()
            if event == "status":
                payload = json.loads(data)
                if payload.get("stage") == "intent":
                    intent = payload.get("intent")
                    t_first_status = t_first_status or now
                elif payload.get("stage") == "agent":
                    # 取证完成的时刻：智能体节点一次性返回，tools 即本轮真实调用
                    tools = payload.get("tools") or []
                    t_evidence = now
            elif event == "token":
                t_first_token = t_first_token or now
                reply_chars += len(json.loads(data).get("content", ""))
            elif event == "items":
                items = len(json.loads(data).get("items") or [])
            elif event == "done":
                t_done = now
    t_end = time.perf_counter()
    return {
        "question": question,
        "intent": intent,
        "tools": tools,
        "evidence_ms": round((t_evidence - t0) * 1000) if t_evidence else None,
        "ttfb_status_ms": round((t_first_status - t0) * 1000) if t_first_status else None,
        "ttfb_token_ms": round((t_first_token - t0) * 1000) if t_first_token else None,
        "total_ms": round(((t_done or t_end) - t0) * 1000),
        "reply_chars": reply_chars,
        "item_cards": items,
    }


def summarize(rows: list[dict], label: str) -> dict:
    def pct(vals, q):
        s = sorted(vals)
        return s[max(0, int(len(s) * q) - 1)]

    return {
        "group": label,
        "n": len(rows),
        "ttfb_token_avg_ms": round(statistics.mean(r["ttfb_token_ms"] for r in rows if r["ttfb_token_ms"])),
        "ttfb_token_p95_ms": pct([r["ttfb_token_ms"] for r in rows if r["ttfb_token_ms"]], 0.95),
        "total_avg_ms": round(statistics.mean(r["total_ms"] for r in rows)),
        "total_p95_ms": pct([r["total_ms"] for r in rows], 0.95),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rounds", type=int, default=1, help="每组问题重复轮数（默认 1）")
    args = parser.parse_args()

    from app.config import settings

    base_url = "http://127.0.0.1:8000"
    headers = {"X-Internal-Key": settings.JAVA_INTERNAL_KEY}

    health = requests.get(f"{base_url}/health", timeout=10)
    health.raise_for_status()
    print(f"服务健康检查通过：{health.json()}")

    rows: list[dict] = []
    for round_no in range(1, args.rounds + 1):
        for group, q in QUESTIONS:
            row = measure_one(base_url, headers, q)
            row["group"] = group
            row["round"] = round_no
            rows.append(row)
            print(
                f"  [r{round_no}][{group}] {q!r} → 规则标签={row['intent']!r} "
                f"工具={row['tools']} 取证={row['evidence_ms']}ms "
                f"首token={row['ttfb_token_ms']}ms 整轮={row['total_ms']}ms "
                f"卡片={row['item_cards']} 回复{row['reply_chars']}字"
            )

    groups = sorted({r["group"] for r in rows})
    summary = [summarize([r for r in rows if r["group"] == g], g) for g in groups]
    summary.append(summarize(rows, "all"))

    print("\n## AI 客服流式响应性能\n")
    print("| 分组 | 样本 | 首token均值 | 首token P95 | 整轮均值 | 整轮 P95 |")
    print("|---|---|---|---|---|---|")
    for s in summary:
        print(
            f"| {s['group']} | {s['n']} | {s['ttfb_token_avg_ms']} ms | {s['ttfb_token_p95_ms']} ms "
            f"| {s['total_avg_ms']} ms | {s['total_p95_ms']} ms |"
        )

    RESULT_DIR.mkdir(exist_ok=True)
    out_path = RESULT_DIR / "latency_eval.json"
    out_path.write_text(
        json.dumps(
            {
            **provenance(),
                "rounds": args.rounds,
                # smalltalk 由规则前置直出；其余两支各"规划 + 作答"两次 LLM——
                # 一句多诉求仍是 2 次（工具在同一规划步里并行），这是改造的关键收益
                "llm_calls_per_branch": {
                    "smalltalk": 1, "faq": 2, "recommend": 2,
                    "recommend_pre_refactor": 3, "multi_intent_pre_refactor": "只能答一半",
                },
                "tool_execution": "真实（Java 8080 在跑，回调取数）",
                "detail": rows,
                "summary": summary,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\n结果已写入：{out_path}")


if __name__ == "__main__":
    main()
