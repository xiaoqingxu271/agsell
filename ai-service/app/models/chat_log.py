"""ai_chat_log 表 ORM 模型（对话审计日志，每轮一条）

与 kb_hit_log（本地 SQLite，只做运营统计聚合）分工：审计要求逐轮原文可查、
可按会话/用户追溯，落共享业务库由 Java 管理端直读（与 ai_ticket 同模式——
Python 写、Java 读，两端不经 HTTP 中转）。
"""
from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Integer, SmallInteger, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class ChatLog(Base):
    __tablename__ = "ai_chat_log"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True, comment="用户 id（游客/未登录为 null）")
    session_id: Mapped[str] = mapped_column(String(64), comment="会话 id")
    question: Mapped[str] = mapped_column(Text, comment="用户本轮提问原文")
    reply: Mapped[str | None] = mapped_column(Text, nullable=True, comment="AI 本轮回复")
    intent: Mapped[str | None] = mapped_column(String(32), nullable=True, comment="意图标签（规则前置命中才非空）")
    tools: Mapped[str | None] = mapped_column(String(255), nullable=True, comment="本轮调用的工具，逗号分隔")
    refused: Mapped[int] = mapped_column(SmallInteger, default=0, comment="是否拒答轮 0/1")
    hits: Mapped[int] = mapped_column(Integer, default=0, comment="知识库命中条数")
    refs: Mapped[str | None] = mapped_column(String(1000), nullable=True, comment="引用的 FAQ id，逗号分隔")
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True, comment="本轮总耗时毫秒")
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), comment="创建时间"
    )
