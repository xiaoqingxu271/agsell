"""智能体工具编排评估（实验七）：模型自选工具的正确率、代价与闸门是否仍在

回答的问题：把"调哪个工具"从人工 if-else 换成大模型 function calling 之后，
它选得对不对、多快、会不会乱调，以及已实测生效的闸门（域外导购拒答）有没有被绕过。

口径（诚实声明，引用时必须一起写）：
- LLM / FAQ 检索 / 商品向量召回：全部真实（Agnes agnes-2.5-flash + 本地 bge + Chroma + MySQL）；
- Java 主系统：桩替换（返回固定订单/物流/候选商品），因为这里测的是"选什么工具"，
  不是"取数对不对"。执行结果的正确性由 Java 侧单测与端到端验证负责。
- 意图标签 → 期望工具的映射是唯一的判分依据；hold-out 测试集的 intent 标注与
  实验二/三完全同源，因此"规则 vs 大模型 vs 智能体"三者可横向比较。

通道：
  plan —— 只跑 agent 节点（选择并执行工具），每题 1 次 LLM 调用起步，测"选得对不对"；
  full —— 跑完整图（含生成），测整轮延迟与最终回复，额度消耗更大，建议配合 --limit。

用法（在 ai-service 目录下）：
    .venv/Scripts/python scripts/eval_agent.py --testset holdout --mode plan --workers 1
    .venv/Scripts/python scripts/eval_agent.py --testset holdout --mode plan --limit 45 --workers 1
    .venv/Scripts/python scripts/eval_agent.py --testset holdout --mode full --limit 20 --workers 1

输出：控制台 Markdown 报告 + scripts/eval_results/agent_eval_<mode>[_<testset>][_n<条数>].json
探针题（multi_intent / context_dependent / out_of_domain）不参与"准确率"计算，
单独成组报告——它们是本轮改造要新增的能力（一句多诉求、域外拒答），旧架构没有对应指标。
"""
import argparse
import json
import sys
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

from _provenance import provenance  # noqa: E402
from eval_intent import load_testset, sample_cases  # noqa: E402

# 意图标签 → 期望调用的工具（agent 路径的"正确答案"）
EXPECTED_TOOL = {
    "faq": "kb_search",
    "recommend": "product_search",
    "order": "order_query",
    "logistics": "logistics_query",
    "after_sales": "after_sales_query",
    "human": "escalate_human",
    # 纯寒暄不该取数：一次工具调用都不要（规则前置已短路，agent 也不该被叫起来）
    "smalltalk": None,
}


class StubJavaApi:
    """Java 内部接口的功能桩：结构与真实 VO 同形，取数结果固定。

    存在意义不是"省时间"，而是把测量面收窄到规划层：真实 Java 未启动时，
    所有业务工具都会因连不上而返回 error，观察状态一变，反思重试分支会被误触发，
    延迟与调用数都不再反映模型行为。
    """

    def __init__(self):
        self.calls = []

    def query_orders(self, user_id, status=None):
        self.calls.append("order_query")
        return [{"orderNo": "AGS202610050001", "status": 2, "statusText": "待收货", "payAmount": 39.9}]

    def query_order_detail(self, user_id, order_no):
        self.calls.append("order_query")
        return {"orderNo": order_no or "AGS202610050001", "status": 2,
                "statusText": "待收货", "logType": "顺丰", "logNo": "SF123456"}

    def query_logistics(self, user_id, order_no):
        self.calls.append("logistics_query")
        return {"orderNo": order_no or "AGS202610050001", "status": 2, "statusText": "待收货",
                "logType": "顺丰", "logNo": "SF123456"}

    def query_after_sales(self, user_id, status=None):
        self.calls.append("after_sales_query")
        return [{"afterSalesNo": "AS202610050001", "status": 0, "statusText": "待处理", "refundAmount": 20}]

    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        self.calls.append("product_search")
        if ids:
            pool = {101: "赣南脐橙 10斤", 102: "五常大米 5kg"}
            return [{"id": i, "name": pool.get(i, "在售农产品"), "price": 19.9,
                     "sales": 500, "origin": "江西赣州"} for i in ids]
        if keyword and keyword not in ("水果", "橙子", "柑橘", "大米", "茶叶"):
            return []  # 跑步鞋/手机壳这类域外词：库里确实没有
        return [{"id": 101, "name": "赣南脐橙 10斤", "price": 19.9, "sales": 500, "origin": "江西赣州"}]

    def fetch_product(self, product_id):
        return None

    def format_payload(self, data):
        return json.dumps(data, ensure_ascii=False, default=str)


class CountingAgentLLM:
    """真实 LLM 的计数包装：规划调用与作答调用分开统计，429 走长退避。

    为什么必须分开：改造前只有一条分类调用，现在同一个模型既当规划器又当作答器，
    混在一起就分不清"额度花在选工具上还是花在写回复上"。
    ⚠ 沿用实验三的教训：429 是基础设施故障，不是模型能力——只要出现最终失败的调用，
    本次测量整体作废（valid=false + .INVALID 后缀），不进论文。
    """

    def __init__(self, inner, schemas, retry_wait=20.0, retries=3):
        self._inner = inner
        self._planner = inner.bind_tools(schemas)
        self._retry_wait = retry_wait
        self._retries = retries
        self.plan_calls = 0
        self.answer_calls = 0
        self.errors = 0
        self.rate_limited = 0

    def bind_tools(self, schemas, **kwargs):
        return self  # agent 节点会再绑一次，幂等返回自身即可

    def invoke(self, messages, **kwargs):
        from langchain_core.messages import SystemMessage

        sys_text = next((m.content for m in messages if isinstance(m, SystemMessage)), "")
        planning = "工具编排器" in sys_text
        target = self._planner if planning else self._inner
        if planning:
            self.plan_calls += 1
        else:
            self.answer_calls += 1
        last = None
        for attempt in range(self._retries + 1):
            try:
                return target.invoke(messages, **kwargs)
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


def build_stack():
    """真实检索 + 真实商品向量召回 + 桩 Java（口径见模块 docstring）"""
    from dotenv import load_dotenv

    load_dotenv(PROJECT_ROOT / ".env")
    from langchain_openai import ChatOpenAI

    from app.agent.tool_defs import TOOL_SCHEMAS
    from app.config import settings
    from app.db import SessionLocal
    from app.rag.personalize import Personalizer
    from app.rag.product_store import PRODUCT_COLLECTION, ProductStore
    from app.rag.retriever import Retriever
    from app.rag.vectorstore import VectorStore
    from app.repositories import faq_repo

    from fastembed import TextEmbedding

    embedder = TextEmbedding(settings.EMBEDDING_MODEL)
    vs = VectorStore(path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL, embedder=embedder)
    retriever = Retriever(vector_store=vs, faq_repo=faq_repo, session_factory=SessionLocal)
    product_store = ProductStore(VectorStore(
        path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL,
        embedder=embedder, collection=PRODUCT_COLLECTION))
    personalizer = Personalizer(SessionLocal)

    llm = CountingAgentLLM(
        ChatOpenAI(model=settings.AGNES_MODEL, base_url=settings.AGNES_BASE_URL,
                   api_key=settings.AGNES_API_KEY, temperature=settings.AGNES_TEMPERATURE,
                   timeout=settings.LLM_TIMEOUT, max_retries=settings.LLM_MAX_RETRIES),
        TOOL_SCHEMAS,
    )
    return llm, retriever, product_store, personalizer


def score_case(case: dict, tools: list[str], statuses: list[str], items: list,
               route: str = "agent") -> dict:
    """判分口径：工具选对 = 期望工具在实调集合里；smalltalk 的期望是"一个都不调"。

    规则前置短路（human / 纯寒暄）也算命中：这两类的正确出口本来就是不进智能体
    （毫秒级、零额度），把它们记成"没调 escalate_human"等于惩罚省下来的那次调用。
    """
    expected = EXPECTED_TOOL.get(case["intent"], "kb_search")
    if route == "rule_shortcut":
        hit = case["intent"] in ("human", "smalltalk")
    else:
        hit = expected in tools if expected else not tools
    return {
        "q": case["q"],
        "intent": case["intent"],
        "feature": case.get("feature"),
        "expected_tool": expected,
        "tools": tools,
        "statuses": statuses,
        "items": len(items),
        "hit": hit,
        # 超调：多叫了与诉求无关的工具（免费额度与延迟的直接成本）
        "extra_tools": [t for t in tools if t != expected],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["plan", "full"], default="plan")
    parser.add_argument("--testset", default="holdout")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--seed", type=int, default=20261017)
    parser.add_argument("--workers", type=int, default=1, help="并发数（免费额度按窗口配额，建议 1）")
    parser.add_argument("--retry-wait", type=float, default=25.0)
    parser.add_argument("--retries", type=int, default=4)
    args = parser.parse_args()

    all_cases, tag = load_testset(args.testset)
    scored = [c for c in all_cases if "intent" in c]
    # 探针题不在 cases 里（不计分），是测试集文件顶层的 probes 键
    probe_path = {
        "holdout": PROJECT_ROOT / "scripts" / "eval_data" / "intent_testset_holdout.json",
        "tuned": PROJECT_ROOT / "scripts" / "eval_data" / "intent_testset.json",
    }.get(args.testset, Path(args.testset))
    probes: list[dict] = []
    if probe_path.exists():
        probes = json.loads(probe_path.read_text(encoding="utf-8")).get("probes") or []
    cases = sample_cases(scored, args.limit, args.seed)
    sampled_note = (
        f"本次为 {len(scored)} 条可判分样本中的 {len(cases)} 条随机抽样（seed={args.seed}，可复现）"
        "——受免费额度窗口配额限制未跑全集，引用时必须写明是子集"
        if len(cases) < len(scored) else ""
    )

    # .env 的 DATABASE_URL 里 host 是 Docker 专用的 host.docker.internal，本机直连要换掉。
    # 只改 host、凭证一律沿用 .env——脚本里不写死任何账号密码，缺配置就直接拒绝出数而不是猜一份
    import os

    from dotenv import load_dotenv

    load_dotenv(PROJECT_ROOT / ".env")
    if not os.environ.get("DATABASE_URL"):
        raise SystemExit("缺少 DATABASE_URL：请在 ai-service/.env 里配置（本机跑要把 host 换成 127.0.0.1）")
    os.environ["DATABASE_URL"] = os.environ["DATABASE_URL"].replace("host.docker.internal", "127.0.0.1")
    llm, retriever, product_store, personalizer = build_stack()
    from langchain_core.messages import HumanMessage

    from app.agent.executors import ToolContext
    from app.agent.graph import build_graph
    from app.agent.nodes import make_agent_node, make_intent_node

    api = StubJavaApi()
    ctx = ToolContext(retriever=retriever, product_store=product_store,
                      personalizer=personalizer, java_api_module=api)
    agent_node = make_agent_node(llm, ctx)
    intent_node = make_intent_node()
    graph = None
    if args.mode == "full":
        graph = build_graph(llm, retriever, java_api_module=api, checkpointer=None,
                            product_store=product_store, personalizer=personalizer)

    def run_one(case: dict) -> dict:
        base = {"messages": [HumanMessage(content=case["q"])], "userId": 1, "productId": None,
                "observations": [], "rec_items": [], "product_context": "", "product_item": None,
                "intent": "", "order_no": None, "human_fallback": False}
        t0 = time.perf_counter()
        reply = ""
        route = "agent"
        if args.mode == "plan":
            state = {**base, **intent_node(base)}
            if state["intent"] in ("smalltalk", "human"):
                # 规则前置短路：agent 不被调用（这正是省掉一次 LLM 往返的位置）
                route = "rule_shortcut"
                out = {"observations": [], "rec_items": []}
            else:
                out = agent_node(state)
        else:
            result = graph.invoke({"messages": [HumanMessage(content=case["q"])],
                                   "userId": 1, "productId": None})
            out = result
            reply = (result["messages"][-1].content or "").strip()
        ms = round((time.perf_counter() - t0) * 1000)
        obs = out.get("observations") or []
        s = score_case(case, [o["tool"] for o in obs], [o["status"] for o in obs],
                       out.get("rec_items") or [], route)
        s["route"] = route
        s["latency_ms"] = ms
        s["reply"] = reply[:120]
        return s

    results: list[dict] = [None] * len(cases)  # type: ignore

    def worker(i: int, case: dict):
        try:
            results[i] = run_one(case)
        except Exception as e:  # noqa: BLE001 单条失败不影响其余，但会计入 errors
            results[i] = {"q": case["q"], "intent": case["intent"], "feature": case.get("feature"),
                          "expected_tool": EXPECTED_TOOL.get(case["intent"]), "tools": [],
                          "statuses": ["exception"], "items": 0, "hit": False,
                          "extra_tools": [], "latency_ms": None, "route": "agent",
                          "error": f"{type(e).__name__}"}

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        list(pool.map(lambda p: worker(*p), list(enumerate(cases))))

    # 探针题：不参与准确率，单独观察（一句多诉求 / 域外拒答 / 上下文指代）
    probe_results: list[dict] = []
    if args.mode == "plan":
        for case in probes:
            try:
                base = {"messages": [HumanMessage(content=case["q"])], "userId": 1, "productId": None,
                        "observations": [], "rec_items": [], "product_context": "",
                        "product_item": None, "intent": "", "order_no": None, "human_fallback": False}
                state = {**base, **intent_node(base)}
                out = agent_node(state)
                obs = out.get("observations") or []
                probe_results.append({
                    "kind": case["kind"], "q": case["q"], "note": case.get("note", ""),
                    "tools": [o["tool"] for o in obs],
                    "statuses": [o["status"] for o in obs],
                    "args": [o.get("args") for o in obs],
                    "items": len(out.get("rec_items") or []),
                })
            except Exception as e:  # noqa: BLE001
                probe_results.append({"kind": case["kind"], "q": case["q"],
                                      "error": f"{type(e).__name__}"})

    valid = llm.errors == 0
    hit_total = sum(1 for r in results if r["hit"])
    by_intent = {}
    for lb in sorted({c["intent"] for c in cases}):
        rows = [r for r in results if r["intent"] == lb]
        by_intent[lb] = {"total": len(rows), "correct": sum(1 for r in rows if r["hit"]),
                        "recall": round(sum(1 for r in rows if r["hit"]) / len(rows), 4) if rows else None}
    by_feature = {}
    for ft in sorted({c["feature"] for c in cases if c.get("feature")}):
        rows = [r for r in results if r.get("feature") == ft]
        by_feature[ft] = {"total": len(rows), "correct": sum(1 for r in rows if r["hit"]),
                          "accuracy": round(sum(1 for r in rows if r["hit"]) / len(rows), 4) if rows else None}
    lat = [r["latency_ms"] for r in results if r["latency_ms"]]
    lat_sorted = sorted(lat)
    over_calls = Counter(t for r in results for t in r["extra_tools"])
    multi = [r for r in probe_results if r.get("kind") == "multi_intent"]
    ood = [r for r in probe_results if r.get("kind") == "out_of_domain"]
    ood_leaks = [r for r in ood if r.get("items")]

    payload = {
        "mode": args.mode,
        "testset": tag or "tuned",
        **provenance(),
        "valid": valid,
        "sampled": bool(sampled_note),
        "sample_of_total": f"{len(cases)}/{len(scored)}",
        "sample_seed": args.seed,
        "tool_execution": "stub（Java 未参与；测规划层选择与延迟）",
        "llm_calls": {"plan": llm.plan_calls, "answer": llm.answer_calls},
        "llm_errors": llm.errors,
        "rate_limited_calls": llm.rate_limited,
        "accuracy": round(hit_total / len(cases), 4) if cases else None,
        "by_intent": by_intent,
        "by_feature": by_feature,
        "latency_ms": {
            "avg": round(sum(lat) / len(lat)) if lat else None,
            "p50": lat_sorted[len(lat_sorted) // 2] if lat else None,
            "p95": lat_sorted[int(len(lat_sorted) * 0.95) - 1] if lat else None,
            "max": lat_sorted[-1] if lat else None,
        },
        "over_call": dict(over_calls),
        "cases": results,
        "probes": probe_results,
    }
    if sampled_note:
        payload["sampling_note"] = sampled_note

    RESULT_DIR.mkdir(exist_ok=True)
    suffix = f"_n{len(cases)}" if len(cases) < len(scored) else ""
    out_path = RESULT_DIR / f"agent_eval_{args.mode}{'_' + tag if tag else ''}{suffix}.json"
    if not valid:
        out_path = out_path.with_name(out_path.stem + f".{datetime.now():%Y%m%dT%H%M%S}.INVALID")
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n## 智能体工具编排评估 —— mode={args.mode}（测试集：{tag or 'tuned'}）\n")
    print(f"- 可判分样本：{len(cases)} 条{('（' + sampled_note + '）') if sampled_note else ''}")
    print(f"- **工具选择准确率：{hit_total}/{len(cases)} = {(hit_total / len(cases) if cases else 0):.1%}**")
    print(f"- LLM 调用：规划 {llm.plan_calls} 次 / 作答 {llm.answer_calls} 次，"
          f"429 退避 {llm.rate_limited} 次，最终失败 {llm.errors} 次")
    if lat:
        print(f"- 单轮取证延迟：平均 {payload['latency_ms']['avg']} ms，"
              f"P50 {payload['latency_ms']['p50']} ms，P95 {payload['latency_ms']['p95']} ms，"
              f"最大 {payload['latency_ms']['max']} ms")
    if payload.get("provenance_warning"):
        print(f"- ⚠ {payload['provenance_warning']}")
    print("\n### 分意图\n")
    print("| 意图 | 期望工具 | 样本 | 选对 | 召回率 |")
    print("|---|---|---|---|---|")
    for lb, v in by_intent.items():
        print(f"| {lb} | {EXPECTED_TOOL.get(lb) or '（不调用）'} | {v['total']} | "
              f"{v['correct']} | {v['recall']:.1%} |")
    if by_feature:
        print("\n### 分特征（词面线索有无）\n")
        for ft, v in by_feature.items():
            print(f"- {ft}：{v['accuracy']:.1%}（{v['correct']}/{v['total']}）")
    if over_calls:
        print("\n### 超调（与诉求无关的工具）\n")
        for t, n in over_calls.most_common():
            print(f"- {t}：{n} 次")
    if multi:
        print("\n### 探针：一句多诉求（旧架构只能答一半）\n")
        for r in multi:
            print(f"- 「{r['q']}」→ {r['tools']}")
    if ood:
        print("\n### 探针：域外请求闸门（必须 0 泄漏）\n")
        for r in ood:
            print(f"- 「{r['q']}」→ tools={r['tools']} statuses={r['statuses']} 卡片={r.get('items')}")
        print(f"- **泄漏 {len(ood_leaks)}/{len(ood)}**")
    misses = [r for r in results if not r["hit"]]
    if misses:
        print("\n### 选错/未选到期望工具（最多 20 条）\n")
        for r in misses[:20]:
            print(f"- 「{r['q']}」 期望 {r['expected_tool']}，实调 {r['tools']} [{r.get('feature')}]")
    print(f"\n结果已写入：{out_path}")
    if not valid:
        print(f"\n⚠ 本次测量无效：{llm.errors} 次调用最终失败（429 退避 {llm.rate_limited} 次），"
              f"文件已加 .INVALID 后缀，不得作为论文数据。请稍后重试或加大 --retry-wait。")
        sys.exit(1)


if __name__ == "__main__":
    main()
