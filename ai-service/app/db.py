"""数据库连接（SQLAlchemy 2.x，复用 agsell 主库 MySQL）"""
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings

engine = create_engine(
    settings.DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=3600,
    echo=False,
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def get_session():
    """FastAPI 依赖：每请求一个会话"""
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
