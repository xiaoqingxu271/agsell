"""溯源知识入库：生产记录 + 溯源档案切块 → ai_faq(category=trace_knowledge) + 向量索引

需求文档 §6.2.2「长文档切分入库」的落地（第三轮）。数据源是真实的溯源体系数据
（生产记录 / 溯源档案），切块策略见 app/rag/chunker.py。

幂等策略：trace_knowledge 是**派生条目**（源数据在 production_record / traceability_info），
重复运行先清空该类目再插入，不会污染人工撰写的平台规则条目与 seed_kb_extended 生成的
商品域条目。入库后全量重建 FAQ 向量索引（与 POST /v1/admin/kb/index 同路径）。

用法（ai-service 目录下；本地开发请覆盖 DATABASE_URL 指向 127.0.0.1）：
    DATABASE_URL="mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4" \
        .venv/Scripts/python scripts/seed_trace_kb.py
"""
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from sqlalchemy import text  # noqa: E402

from app.db import SessionLocal  # noqa: E402
from app.main import get_vectorstore  # noqa: E402
from app.models.faq import Faq  # noqa: E402
from app.rag.chunker import build_trace_entries  # noqa: E402

TRACE_CATEGORY = "trace_knowledge"


def main() -> None:
    with SessionLocal() as session:
        entries = build_trace_entries(session)
        if not entries:
            print("没有可入库的溯源数据（production_record / traceability_info 为空），未做变更")
            return

        # 派生条目整类重建：先清后插，幂等
        deleted = session.execute(
            text("DELETE FROM ai_faq WHERE category = :c"), {"c": TRACE_CATEGORY}
        ).rowcount
        for e in entries:
            session.add(Faq(
                question=e["question"], answer=e["answer"],
                category=TRACE_CATEGORY, keywords=e["keywords"], status=1,
            ))
        session.commit()

        total = session.execute(
            text("SELECT COUNT(*) FROM ai_faq WHERE status = 1")
        ).scalar()
        print(f"trace_knowledge：清除旧条目 {deleted} 条，写入 {len(entries)} 条；知识库启用总数 {total}")

    # 全量重建 FAQ 向量索引（MySQL 为准，与 POST /v1/admin/kb/index 同路径）
    n = get_vectorstore().rebuild(_all_enabled_entries())
    print(f"FAQ 向量索引已重建：{n} 条")


def _all_enabled_entries() -> list[dict]:
    from app.db import SessionLocal
    from app.repositories import faq_repo

    with SessionLocal() as session:
        return [
            {"id": f.id, "question": f.question, "answer": f.answer,
             "category": f.category, "keywords": f.keywords or ""}
            for f in faq_repo.list_enabled(session)
        ]


if __name__ == "__main__":
    main()
