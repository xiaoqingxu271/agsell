"""ai_faq 表 ORM 模型（FAQ 知识库数据源，管理端可维护）"""
from datetime import datetime

from sqlalchemy import BigInteger, DateTime, SmallInteger, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class Faq(Base):
    __tablename__ = "ai_faq"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    question: Mapped[str] = mapped_column(String(255), comment="问题")
    answer: Mapped[str] = mapped_column(Text, comment="答案")
    category: Mapped[str] = mapped_column(
        String(50), default="general", comment="分类：shipping/aftersales/product/platform 等"
    )
    keywords: Mapped[str] = mapped_column(
        String(255), default="", comment="关键词，逗号分隔，用于关键词召回"
    )
    status: Mapped[int] = mapped_column(SmallInteger, default=1, comment="1=启用 0=停用")
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), comment="创建时间"
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), onupdate=func.now(), comment="更新时间"
    )
