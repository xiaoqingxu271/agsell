"""初始化脚本：建表 + 写入种子 FAQ +（可选）重建索引

用法：
  python scripts/init_faq.py            # 建表 + 仅当表为空时写入种子数据
  python scripts/init_faq.py --force    # 清空后重新写入种子数据
  python scripts/init_faq.py --index    # 写入后立即重建 Chroma 索引
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sqlalchemy import delete

from app.db import SessionLocal, engine
from app.models.faq import Faq
from app.rag.vectorstore import VectorStore
from app.repositories import faq_repo
from scripts.seed_data import SEED_FAQS


def main() -> None:
    parser = argparse.ArgumentParser(description="初始化 FAQ 知识库")
    parser.add_argument("--force", action="store_true", help="清空后重新写入种子数据")
    parser.add_argument("--index", action="store_true", help="写入后重建 Chroma 索引")
    args = parser.parse_args()

    faq_repo.ensure_table()
    print("[OK] ai_faq 表已就绪")

    with SessionLocal() as session:
        if args.force:
            session.execute(delete(Faq))
            session.commit()
            print("[INFO] 已清空 ai_faq")

        existing = faq_repo.count_enabled(session)
        if existing > 0 and not args.force:
            print(f"[SKIP] ai_faq 已有 {existing} 条数据，跳过种子写入（用 --force 可重置）")
        else:
            for item in SEED_FAQS:
                faq_repo.create(session, **item)
            print(f"[OK] 已写入 {len(SEED_FAQS)} 条种子 FAQ")

        if args.index:
            entries = [
                {
                    "id": f.id,
                    "question": f.question,
                    "answer": f.answer,
                    "category": f.category,
                    "keywords": f.keywords,
                }
                for f in faq_repo.list_enabled(session)
            ]
            n = VectorStore().rebuild(entries)
            print(f"[OK] 索引完成：{n} 条")
        else:
            print("[HINT] 未重建索引，可调用 POST /v1/admin/kb/index 或加 --index 参数")


if __name__ == "__main__":
    main()
