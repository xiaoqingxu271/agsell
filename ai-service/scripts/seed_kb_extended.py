"""知识库扩容：从在售商品/类目半自动生成商品域知识条目（ai_faq 行，category 区分）

生成两类条目（幂等：按 question 精确去重，重复运行不产生重复行）：
1. product_knowledge —— 每个在售商品 1 条：卖点/产地/储存/保质期问答
   （商品详情类提问在 FAQ 通道可直答；导购类提问仍由意图路由走 recommend 通道，
   两不冲突）。
2. category_knowledge —— 每个二级类目 1 条：该类目在售商品与价格带汇总。

写 MySQL 后全量重建 FAQ 向量索引（与 POST /v1/admin/kb/index 同路径）。
管理端 FAQ CRUD UI 现成，可按 category 筛出来逐条修订或停用。

用法（ai-service 目录下）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/seed_kb_extended.py
"""
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from sqlalchemy import select  # noqa: E402

from app.db import SessionLocal  # noqa: E402
from app.main import get_vectorstore  # noqa: E402
from app.models.faq import Faq  # noqa: E402
from app.repositories import faq_repo, product_repo  # noqa: E402


def build_entries(session):
    products = product_repo.list_on_sale(session)
    cats = product_repo.category_name_map(session)

    entries = []
    for p in products:
        cat_name = cats.get(p.category_id, "")
        facts = [
            p.subtitle or "",
            f"产地：{p.origin}" if p.origin else "",
            f"属于「{cat_name}」类目" if cat_name else "",
            f"储存方式：{p.storage}" if p.storage else "",
            f"保质期：{p.shelf_life}" if p.shelf_life else "",
            f"当前售价 {float(p.price):.2f} 元",
        ]
        answer = "；".join(x for x in facts if x) + "。可在小程序搜索商品名查看详情或下单。"
        entries.append({
            "question": f"「{p.name}」这个商品有什么特点？",
            "answer": answer,
            "category": "product_knowledge",
            "keywords": p.name.split(" ")[0],
        })

    by_cat: dict[str, list] = {}
    for p in products:
        by_cat.setdefault(cats.get(p.category_id, "其他"), []).append(p)
    for cat_name, items in sorted(by_cat.items()):
        names = "、".join(p.name for p in items[:8])
        lo = min(float(p.price) for p in items)
        hi = max(float(p.price) for p in items)
        more = f"等共 {len(items)} 款" if len(items) > 8 else ""
        entries.append({
            "question": f"「{cat_name}」类目都有哪些商品？",
            "answer": f"在售{len(items)}款：{names}{more}，价格带 {lo:.2f}~{hi:.2f} 元。"
                      f"具体口味规格请看商品详情页，或让我按预算帮您挑。",
            "category": "category_knowledge",
            "keywords": cat_name,
        })
    return entries


def main():
    session = SessionLocal()
    try:
        existing = {q for (q,) in session.execute(select(Faq.question))}
        entries = build_entries(session)
        added = 0
        for e in entries:
            if e["question"] in existing:
                continue
            faq_repo.create(session, e["question"], e["answer"], e["category"], e["keywords"], 1)
            existing.add(e["question"])
            added += 1
        total = faq_repo.count_enabled(session)
        n = get_vectorstore().rebuild([
            {"id": f.id, "question": f.question, "answer": f.answer,
             "category": f.category, "keywords": f.keywords}
            for f in faq_repo.list_enabled(session)
        ])
        print(f"新增条目 {added} 条；FAQ 总数 {total} 条；向量索引重建 {n} 条")
    finally:
        session.close()


if __name__ == "__main__":
    main()
