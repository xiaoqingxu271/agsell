"""生成忠实度评估（LLM-as-judge）：回答是否忠于检索证据

此前七项实验全部集中在检索层（Hit/MRR）与分类层（意图/工具/拒答），
"生成是否忠于检索内容"只有提示词约束与口碑成品句机制，没有量化指标——
这是 RAG 评估链路缺的最后一环，也是论文里必须补的一张表。

口径（诚实声明）：
- 问句：platform_faq 的人工同义改写（与 eval_rag.py 主口径同源，抽样）；
- 检索：真实三路融合（与生产同参）；
- 生成：真 LLM（Agnes），提示词与 AGENT_ANSWER_SYSTEM_TEMPLATE 同口径（只答资料内内容）；
- 判分：LLM-as-judge（同模型自评存在自偏置风险，故 judge 提示词刻意要求"逐断言找证据"，
  并把不支持断言逐条列出；解析失败重试一次，仍失败该条记 parse_error 不计分）。

指标：
- faithfulness（1-5）：回答中的事实断言可被证据支持的程度，均值 + 分布；
- relevance（1-5）：回答对问题的针对性；
- unsupported_claims：judge 列出的无证据断言（人工复核用）；
- low_faith_rate：faithfulness < 4 的占比。

用法（在 ai-service 目录下，需 MySQL + Agnes key + 已建向量索引）：
    .venv/Scripts/python scripts/eval_faithfulness.py --limit 20
输出：控制台报告 + scripts/eval_results/faithfulness_eval.json
"""
import argparse
import json
import re
import sys
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

from _provenance import provenance  # noqa: E402
from eval_rag import PARAPHRASES  # noqa: E402  平台 FAQ 的人工改写问句（主口径同源）

JUDGE_SYSTEM = """你是严格的 RAG 质量评审。给你：用户问题、检索证据（若干条知识条目）、AI 回答。
请只依据证据评审，输出 JSON（不要输出其他任何文字）：
{"faithfulness": 1-5, "relevance": 1-5, "unsupported_claims": ["无证据支持的断言", ...]}

评分定义：
- faithfulness（忠实度）：回答里每个事实性断言（数字/政策/时限/金额/流程）是否都能在证据中找到出处。
  5=全部有出处且无添加；4=基本有出处，有一处模糊概括；3=有一处明显无出处的断言；
  2=多处无出处或与证据矛盾；1=主要内容无中生有。
  注意：礼貌用语、引导语（"请问还有什么可以帮您"）不算断言，不扣分。
- relevance（相关性）：回答是否针对问题本身。5=正面命中问题；3=答了但绕远；1=答非所问。
- unsupported_claims：把回答中证据支持不了的**事实性断言**逐条摘出（没有就空数组）。"""


def build_queries(limit: int, seed: int) -> list[dict]:
    """PARAPHRASES: {faq_id: [问句, 问句]} → 每条 FAQ 取第一句，抽样"""
    items = [{"faq_id": fid, "q": qs[0]} for fid, qs in sorted(PARAPHRASES.items())]
    if limit and limit < len(items):
        step = len(items) / limit
        items = [items[int(i * step)] for i in range(limit)]
    return items


def extract_json(text: str) -> dict | None:
    """judge 输出容错解析：优先整体 JSON，失败抠第一个 {...} 块"""
    text = (text or "").strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", text, re.S)
        if m:
            try:
                return json.loads(m.group(0))
            except json.JSONDecodeError:
                return None
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=20, help="评测问句数（免费额度按窗口配额）")
    parser.add_argument("--retry-wait", type=float, default=25.0)
    parser.add_argument("--retries", type=int, default=4)
    args = parser.parse_args()

    import os

    from dotenv import load_dotenv

    load_dotenv(PROJECT_ROOT / ".env")
    if not os.environ.get("DATABASE_URL"):
        raise SystemExit("缺少 DATABASE_URL：请在 ai-service/.env 里配置（本机跑要把 host 换成 127.0.0.1）")
    os.environ["DATABASE_URL"] = os.environ["DATABASE_URL"].replace("host.docker.internal", "127.0.0.1")

    from langchain_openai import ChatOpenAI

    from app.config import settings
    from app.db import SessionLocal
    from app.prompts import build_agent_answer_prompt, format_context
    from app.rag.retriever import Retriever
    from app.rag.vectorstore import VectorStore
    from app.repositories import faq_repo

    retriever = Retriever(vector_store=VectorStore(), faq_repo=faq_repo, session_factory=SessionLocal)
    llm = ChatOpenAI(model=settings.AGNES_MODEL, base_url=settings.AGNES_BASE_URL,
                     api_key=settings.AGNES_API_KEY, temperature=0.0,
                     timeout=settings.LLM_TIMEOUT, max_retries=0)
    judge = ChatOpenAI(model=settings.AGNES_MODEL, base_url=settings.AGNES_BASE_URL,
                       api_key=settings.AGNES_API_KEY, temperature=0.0,
                       timeout=settings.LLM_TIMEOUT, max_retries=0)

    def invoke_with_retry(model, messages, tag: str):
        last = None
        for attempt in range(args.retries + 1):
            try:
                return model.invoke(messages)
            except Exception as e:  # noqa: BLE001
                last = e
                if "429" in f"{type(e).__name__} {e}" and attempt < args.retries:
                    time.sleep(args.retry_wait * (attempt + 1))
                    continue
                break
        raise RuntimeError(f"{tag} 调用最终失败：{type(last).__name__}") from last

    queries = build_queries(args.limit, 20261017)
    results: list[dict] = []
    for i, item in enumerate(queries, 1):
        hits = retriever.retrieve(item["q"], top_k=settings.KB_TOP_K)
        evidence = hits[:3]
        if not evidence:
            results.append({**item, "skipped": "kb_empty", "faithfulness": None,
                            "relevance": None, "unsupported_claims": []})
            print(f"[{i}/{len(queries)}] {item['q'][:24]}… → 检索为空（拒答路径），跳过")
            continue
        ctx = format_context(evidence)
        sys_prompt = build_agent_answer_prompt(ctx, settings.PLATFORM_NAME, settings.SERVICE_PHONE)
        try:
            resp = invoke_with_retry(llm, [(("system", sys_prompt)), (("human", item["q"]))], "generate")
            answer = (resp.content or "").strip() if isinstance(resp.content, str) else str(resp.content)
            judge_msgs = [
                ("system", JUDGE_SYSTEM),
                ("human", f"【用户问题】\n{item['q']}\n\n【检索证据】\n{ctx}\n\n【AI 回答】\n{answer}"),
            ]
            verdict = None
            for _ in range(2):  # 解析失败重试一次
                jresp = invoke_with_retry(judge, judge_msgs, "judge")
                verdict = extract_json(jresp.content if isinstance(jresp.content, str) else "")
                if verdict is not None:
                    break
            if verdict is None:
                results.append({**item, "skipped": "judge_parse_error", "faithfulness": None,
                                "relevance": None, "unsupported_claims": [], "answer": answer[:200]})
                print(f"[{i}/{len(queries)}] {item['q'][:24]}… → judge 解析失败")
                continue
            results.append({
                **item,
                "answer": answer[:400],
                "evidence_ids": [h["id"] for h in evidence],
                "faithfulness": int(verdict.get("faithfulness") or 0),
                "relevance": int(verdict.get("relevance") or 0),
                "unsupported_claims": verdict.get("unsupported_claims") or [],
            })
            print(f"[{i}/{len(queries)}] {item['q'][:24]}… → 忠实 {verdict.get('faithfulness')} / 相关 {verdict.get('relevance')}")
        except RuntimeError as e:
            results.append({**item, "skipped": str(e)[:120], "faithfulness": None,
                            "relevance": None, "unsupported_claims": []})
            print(f"[{i}/{len(queries)}] {item['q'][:24]}… → {e}")

    scored = [r for r in results if isinstance(r.get("faithfulness"), int) and r["faithfulness"] > 0]
    faithful = [r["faithfulness"] for r in scored]
    relevant = [r["relevance"] for r in scored]
    low = [r for r in scored if r["faithfulness"] < 4]
    claims = [c for r in scored for c in r["unsupported_claims"]]

    summary = {
        "n_total": len(results),
        "n_scored": len(scored),
        "n_kb_empty": sum(1 for r in results if r.get("skipped") == "kb_empty"),
        "n_parse_error": sum(1 for r in results if r.get("skipped") == "judge_parse_error"),
        "faithfulness_mean": round(sum(faithful) / len(faithful), 3) if faithful else None,
        "faithfulness_dist": {str(k): faithful.count(k) for k in sorted(set(faithful))},
        "relevance_mean": round(sum(relevant) / len(relevant), 3) if relevant else None,
        "low_faith_rate": round(len(low) / len(scored), 3) if scored else None,
        "unsupported_claim_count": len(claims),
        "unsupported_claims_sample": claims[:10],
    }

    print("\n===== 生成忠实度（LLM-as-judge）=====")
    print(f"计分 {summary['n_scored']}/{summary['n_total']}（检索空 {summary['n_kb_empty']} / 判分解析失败 {summary['n_parse_error']}）")
    if faithful:
        print(f"faithfulness 均值 {summary['faithfulness_mean']}  分布 {summary['faithfulness_dist']}")
        print(f"relevance 均值 {summary['relevance_mean']}")
        print(f"低忠实率(<4) {summary['low_faith_rate']}  无证据断言 {len(claims)} 条")
        for c in claims[:5]:
            print(f"  · {c}")

    RESULT_DIR.mkdir(exist_ok=True)
    out = RESULT_DIR / "faithfulness_eval.json"
    out.write_text(json.dumps({
        **provenance({"limit": args.limit, "judge_model": settings.AGNES_MODEL}),
        "summary": summary,
        "results": results,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n存档：{out}")


if __name__ == "__main__":
    main()
