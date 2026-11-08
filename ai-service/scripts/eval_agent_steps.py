"""智能体步数上限消融：AGENT_MAX_STEPS ∈ {1, 2, 3} 对工具选择正确率/调用数/延迟的影响

回答的问题：生产档 AGENT_MAX_STEPS=2 是针对免费网关延迟（6~15s/步）与不收敛问题的
务实取舍——这个取舍的代价到底多大？步数放开到 3 能多换回多少正确率？压到 1 能省多少延迟？
给论文"有界编排"一节一个量化的取舍依据，而不是一句"我们试过了"。

口径（与 eval_agent.py plan 模式完全一致，唯一变量是步数上限）：
- LLM / FAQ 检索 / 商品向量召回：真实；Java：桩（测的是"选什么工具"）；
- 步数注入方式：直接写 runtime_config 的进程内缓存（ai_agent_max_steps 键），
  生产读的正是这条路径（管理端「AI 运营配置」页同款），**不落库**——退出进程即消失，
  不污染 sys_config；
- 每档跑同一批题（同 seed 抽样，可复现），报告正确率 / 平均调用数 / 平均时延 /
  多诉求探针覆盖。

用法（在 ai-service 目录下，需 MySQL + Agnes key + 已建向量索引）：
    .venv/Scripts/python scripts/eval_agent_steps.py --limit 30 --workers 1
输出：控制台对比表 + scripts/eval_results/agent_steps_eval.json
"""
import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

from _provenance import provenance  # noqa: E402
from eval_agent import EXPECTED_TOOL, StubJavaApi, build_stack, score_case  # noqa: E402
from eval_intent import load_testset, sample_cases  # noqa: E402

STEPS_GRID = [1, 2, 3]


def inject_steps(steps: int) -> None:
    """把步数上限钉进 runtime_config.effective（不落库，详见模块 docstring）。

    实现注记：不能只写 _cache——effective 内部 _load() 的 TTL 过期会用真实
    sys_config 内容**覆盖掉注入值**（E2E 实测三档全部退化成默认 2，准确率全等）。
    monkeypatch effective 对目标键固定返回，其余键透传真实实现（闸门阈值等照常生效）。
    """
    from app import runtime_config as rc

    real_effective = rc.effective

    def pinned(key: str):
        if key == "ai_agent_max_steps":
            return steps
        return real_effective(key)

    rc.effective = pinned


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--testset", default="holdout")
    parser.add_argument("--limit", type=int, default=30, help="每档评测题数（免费额度按窗口配额）")
    parser.add_argument("--seed", type=int, default=20261017)
    parser.add_argument("--workers", type=int, default=1)
    args = parser.parse_args()

    import os

    from dotenv import load_dotenv

    load_dotenv(PROJECT_ROOT / ".env")
    if not os.environ.get("DATABASE_URL"):
        raise SystemExit("缺少 DATABASE_URL：请在 ai-service/.env 里配置（本机跑要把 host 换成 127.0.0.1）")
    os.environ["DATABASE_URL"] = os.environ["DATABASE_URL"].replace("host.docker.internal", "127.0.0.1")

    llm, retriever, product_store, personalizer = build_stack()

    from langchain_core.messages import HumanMessage

    from app.agent.executors import ToolContext
    from app.agent.nodes import make_agent_node, make_intent_node

    api = StubJavaApi()
    ctx = ToolContext(retriever=retriever, product_store=product_store,
                      personalizer=personalizer, java_api_module=api)
    agent_node = make_agent_node(llm, ctx)
    intent_node = make_intent_node()

    all_cases, _tag = load_testset(args.testset)
    scored = [c for c in all_cases if "intent" in c]
    cases = sample_cases(scored, args.limit, args.seed)
    note = (f"从 {len(scored)} 条抽样 {len(cases)} 条（seed={args.seed}）"
            if len(cases) < len(scored) else "全集")

    def run_one(case: dict) -> dict:
        base = {"messages": [HumanMessage(content=case["q"])], "userId": 1, "productId": None,
                "observations": [], "rec_items": [], "product_context": "", "product_item": None,
                "intent": "", "order_no": None, "human_fallback": False}
        t0 = time.perf_counter()
        state = {**base, **intent_node(base)}
        if state["intent"] in ("smalltalk", "human"):
            route, out = "rule_shortcut", {"observations": [], "rec_items": []}
        else:
            route, out = "agent", agent_node(state)
        ms = round((time.perf_counter() - t0) * 1000)
        obs = out.get("observations") or []
        s = score_case(case, [o["tool"] for o in obs], [o["status"] for o in obs],
                       out.get("rec_items") or [], route)
        s["route"] = route  # 漏写会让汇总里"agent 行平均调用数"过滤永假（实测 avg 恒 0.0 的根因）
        s["latency_ms"] = ms
        s["n_calls"] = len(obs)
        return s

    grid_results = []
    for steps in STEPS_GRID:
        inject_steps(steps)
        results: list = [None] * len(cases)  # type: ignore

        def worker(i: int, case: dict):
            try:
                results[i] = run_one(case)
            except Exception as e:  # noqa: BLE001 单条失败记异常，不计分
                results[i] = {"q": case["q"], "intent": case["intent"], "hit": False,
                              "tools": [], "statuses": ["exception"], "n_calls": 0,
                              "extra_tools": [], "latency_ms": None, "route": "agent",
                              "error": type(e).__name__}

        t0 = time.perf_counter()
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            list(pool.map(lambda p: worker(*p), list(enumerate(cases))))
        wall = round(time.perf_counter() - t0, 1)

        scored_rows = [r for r in results if r and "error" not in r]
        lat = [r["latency_ms"] for r in scored_rows if r.get("latency_ms")]
        calls = [r["n_calls"] for r in scored_rows if r.get("route") == "agent"]
        row = {
            "max_steps": steps,
            "n": len(scored_rows),
            "accuracy": round(sum(1 for r in scored_rows if r["hit"]) / len(scored_rows), 3) if scored_rows else None,
            "avg_tool_calls": round(sum(calls) / len(calls), 2) if calls else 0.0,
            "avg_latency_ms": round(sum(lat) / len(lat)) if lat else None,
            "wall_seconds": wall,
            "errors": len(results) - len(scored_rows),
            # 每题明细必须存档：汇总数字异常时（如 avg=0 但准确率>0）才能事后定位，
            # 也满足论文"数字与存档同源可核查"的要求
            "cases": [
                {k: r.get(k) for k in ("q", "intent", "route", "hit", "tools", "n_calls", "latency_ms")}
                for r in scored_rows
            ],
        }
        grid_results.append(row)
        print(f"steps={steps}: 准确率 {row['accuracy']}  平均调用 {row['avg_tool_calls']}  "
              f"平均时延 {row['avg_latency_ms']}ms  墙钟 {wall}s  异常 {row['errors']}")

    print(f"\n（{note}；Java 为桩，口径与 eval_agent.py plan 模式一致，三档横向可比）")
    RESULT_DIR.mkdir(exist_ok=True)
    out = RESULT_DIR / "agent_steps_eval.json"
    out.write_text(json.dumps({
        **provenance({"limit": args.limit, "seed": args.seed, "grid": STEPS_GRID, "sampling": note}),
        "rows": grid_results,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"存档：{out}")


if __name__ == "__main__":
    main()
