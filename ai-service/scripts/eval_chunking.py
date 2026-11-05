"""切分策略对比实验：同一份溯源原始数据，不同 (块长, 重叠) 组合的检索命中对比

回答的问题：溯源切块现在用 180/30（句子边界），这是拍脑袋还是有依据？
本实验固定查询集与判定口径，只变切块粒度，量出"块长-命中率"曲线，给论文一个
"切分策略选型"小节的数据支撑。

口径：
- 数据：MySQL production_record + traceability_info 原始行（与 seed_trace_kb.py 同源 SQL）；
- 每档 (size, overlap) 用 chunker.split_chunks 现切，灌进**独立临时 collection**
  （chroma 子目录 tmp_chunk_eval/，不碰生产 faq 索引）；
- 查询集：从"生产档位条目"反向构造——每条生产记录查"{商品名}的{类型}记录"，
  每份溯源档案查"{商品名}的溯源档案"；命中判定 = top-k 的答案文本里含期望锚点
  （生产记录 → record_date + 批次号；溯源档案 → 批次号）。
- 纯本地推理（bge 编码），零 LLM 额度，可反复跑。

用法（在 ai-service 目录下，需 MySQL 有溯源数据）：
    .venv/Scripts/python scripts/eval_chunking.py
输出：控制台对比表 + scripts/eval_results/chunking_eval.json
"""
import argparse
import json
import shutil
import sys
import tempfile
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

RESULT_DIR = PROJECT_ROOT / "scripts" / "eval_results"

from _provenance import provenance  # noqa: E402

# 待对比的 (块长, 重叠) 组合：覆盖"碎-标准-宽"三档与生产档（180/30）
GRIDS = [(120, 20), (180, 30), (240, 45), (320, 60), (480, 90)]

_RECORD_TYPE_TEXT = {
    "SEEDING": "播种", "FERTILIZING": "施肥", "WATERING": "浇水",
    "PEST_CONTROL": "病虫害防治", "HARVEST": "采收",
}


def load_source_rows(session):
    """与 app.rag.chunker.build_trace_entries 完全同源的原始数据查询"""
    from sqlalchemy import text

    records = session.execute(text(
        """
        select pr.id, pr.record_type, pr.record_date, pr.content, pr.batch_no,
               p.name as product_name
        from production_record pr
        join product p on p.id = pr.product_id
        where p.deleted = 0
        order by pr.product_id, pr.record_date
        """
    )).all()
    traces = session.execute(text(
        """
        select t.batch_no, t.farmer_name, t.origin_province, t.origin_city,
               t.harvest_date, t.quality_check_result, t.certification_type,
               p.name as product_name
        from traceability_info t
        join product p on p.id = t.product_id
        where p.deleted = 0
        """
    )).all()
    return records, traces


def build_corpus(records, traces):
    """原始行 → {chunks: 切块素材列表, queries: [(查询, 期望锚点子串列表)]}

    切块素材与查询解耦：素材进各档位现切，查询锚点不随档位变，命中判定才可比。
    """
    from app.rag.chunker import strip_html

    corpus_docs: list[str] = []
    queries: list[dict] = []

    for _rid, rec_type, rec_date, content, batch_no, product_name in records:
        type_text = _RECORD_TYPE_TEXT.get(str(rec_type).upper(), str(rec_type))
        body = f"{product_name}（批次 {batch_no}）{type_text}记录：{rec_date}，{strip_html(content)}"
        corpus_docs.append(body)
        anchors = [x for x in (str(rec_date or ""), str(batch_no or "")) if x]
        queries.append({"q": f"{product_name}的{type_text}记录", "anchors": anchors, "kind": "record"})

    for batch_no, farmer, prov, city, harvest, quality, cert, product_name in traces:
        parts = [f"批次 {batch_no}"]
        if farmer:
            parts.append(f"种植户 {farmer}")
        if prov or city:
            parts.append(f"产地 {prov or ''}{city or ''}")
        if harvest:
            parts.append(f"采摘日期 {harvest}")
        if quality:
            parts.append(f"质检 {quality}")
        if cert:
            parts.append(f"认证 {cert}")
        body = "｜".join(str(x) for x in parts if x)
        corpus_docs.append(f"{product_name}溯源档案：{body}")
        queries.append({"q": f"{product_name}的溯源档案",
                        "anchors": [str(batch_no or "")] if batch_no else [], "kind": "trace"})

    return corpus_docs, queries


def eval_grid(corpus_docs, queries, size, overlap, embedder, tmp_root) -> dict:
    """单档：切块 → 临时 collection → 逐查询 top3 检索 → 锚点命中"""
    from app.rag.chunker import split_chunks

    chunks: list[str] = []
    for doc in corpus_docs:
        chunks.extend(split_chunks(doc, size=size, overlap=overlap))
    if not chunks:
        return {"size": size, "overlap": overlap, "chunks": 0, "hit1": None, "hit3": None, "avg_ms": None}

    import chromadb

    persist = tmp_root / f"g{size}_{overlap}"
    client = chromadb.PersistentClient(path=str(persist))
    col = client.get_or_create_collection(
        name="chunk_eval", metadata={"hnsw:space": "cosine"})
    embeddings = [v.tolist() for v in embedder.embed(chunks)]
    col.add(ids=[str(i) for i in range(len(chunks))], documents=chunks, embeddings=embeddings)

    hit1 = hit3 = 0
    total_ms = 0.0
    for item in queries:
        t0 = time.perf_counter()
        res = col.query(query_texts=[item["q"]], n_results=min(3, len(chunks)))
        total_ms += (time.perf_counter() - t0) * 1000
        docs = res["documents"][0] if res["documents"] else []
        found = [any(a and a in doc for a in item["anchors"]) for doc in docs]
        if found and found[0]:
            hit1 += 1
        if any(found):
            hit3 += 1
    n = len(queries)
    shutil.rmtree(persist, ignore_errors=True)
    return {
        "size": size, "overlap": overlap, "chunks": len(chunks),
        "hit1": round(hit1 / n, 3), "hit3": round(hit3 / n, 3),
        "avg_ms": round(total_ms / n, 1),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=None, help="覆盖嵌入模型（默认 settings.EMBEDDING_MODEL）")
    args = parser.parse_args()

    import os

    from dotenv import load_dotenv

    load_dotenv(PROJECT_ROOT / ".env")
    if not os.environ.get("DATABASE_URL"):
        raise SystemExit("缺少 DATABASE_URL：请在 ai-service/.env 里配置（本机跑要把 host 换成 127.0.0.1）")
    os.environ["DATABASE_URL"] = os.environ["DATABASE_URL"].replace("host.docker.internal", "127.0.0.1")

    from fastembed import TextEmbedding

    from app.config import settings
    from app.db import SessionLocal

    model_name = args.model or settings.EMBEDDING_MODEL
    embedder = TextEmbedding(model_name)

    session = SessionLocal()
    try:
        records, traces = load_source_rows(session)
    finally:
        session.close()
    if not records and not traces:
        raise SystemExit("库里没有溯源数据（production_record / traceability_info）——先跑 sql/seed_catalog.sql 与 seed_trace_kb.py")

    corpus_docs, queries = build_corpus(records, traces)
    tmp_root = Path(tempfile.mkdtemp(prefix="agsell_chunk_eval_"))
    print(f"数据：{len(records)} 条生产记录 / {len(traces)} 份溯源档案，查询 {len(queries)} 条，素材 {len(corpus_docs)} 篇")
    print(f"{'档位(size/overlap)':>18} | {'块数':>5} | {'Hit@1':>6} | {'Hit@3':>6} | {'P50延迟ms':>9}")
    rows = []
    try:
        for size, overlap in GRIDS:
            r = eval_grid(corpus_docs, queries, size, overlap, embedder, tmp_root)
            rows.append(r)
            print(f"{f'{size}/{overlap}':>18} | {r['chunks']:>5} | "
                  f"{'' if r['hit1'] is None else format(r['hit1'] * 100, '.1f') + '%':>6} | "
                  f"{'' if r['hit3'] is None else format(r['hit3'] * 100, '.1f') + '%':>6} | "
                  f"{'' if r['avg_ms'] is None else r['avg_ms']:>9}")
    finally:
        shutil.rmtree(tmp_root, ignore_errors=True)

    RESULT_DIR.mkdir(exist_ok=True)
    out = RESULT_DIR / "chunking_eval.json"
    out.write_text(json.dumps({
        **provenance({"embedding_model": model_name,
                      "grids": [list(g) for g in GRIDS],
                      "n_queries": len(queries),
                      "n_source_docs": len(corpus_docs)}),
        "rows": rows,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n存档：{out}")


if __name__ == "__main__":
    main()
