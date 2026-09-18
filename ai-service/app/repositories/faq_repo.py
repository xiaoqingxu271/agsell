"""ai_faq 仓储：建表 + 增删改查（数据源在 MySQL，Chroma 只是检索索引）"""
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.db import Base, engine
from app.models.faq import Faq


def ensure_table() -> None:
    """建表（幂等）：首次运行时创建 ai_faq"""
    Base.metadata.create_all(bind=engine)


def list_enabled(session: Session) -> list[Faq]:
    return list(session.scalars(select(Faq).where(Faq.status == 1).order_by(Faq.id)))


def list_all(session: Session, category: str | None = None, page: int = 1, size: int = 20) -> tuple[list[Faq], int]:
    stmt = select(Faq)
    count_stmt = select(Faq.id)
    if category:
        stmt = stmt.where(Faq.category == category)
        count_stmt = count_stmt.where(Faq.category == category)
    total = len(session.execute(count_stmt).all())
    items = list(
        session.scalars(
            stmt.order_by(Faq.id.desc()).offset((page - 1) * size).limit(size)
        )
    )
    return items, total


def create(session: Session, question: str, answer: str, category: str, keywords: str, status: int = 1) -> Faq:
    faq = Faq(question=question, answer=answer, category=category, keywords=keywords, status=status)
    session.add(faq)
    session.commit()
    session.refresh(faq)
    return faq


def update_faq(session: Session, faq_id: int, **fields) -> Faq | None:
    allowed = {"question", "answer", "category", "keywords", "status"}
    data = {k: v for k, v in fields.items() if k in allowed}
    if not data:
        return None
    session.execute(update(Faq).where(Faq.id == faq_id).values(**data))
    session.commit()
    return session.get(Faq, faq_id)


def delete(session: Session, faq_id: int) -> bool:
    faq = session.get(Faq, faq_id)
    if not faq:
        return False
    session.delete(faq)
    session.commit()
    return True


def count_enabled(session: Session) -> int:
    return len(session.execute(select(Faq.id).where(Faq.status == 1)).all())
