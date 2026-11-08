"""对话审计落库（ai_chat_log，MySQL）：每轮一条，管理端审计页只读。

与 kb_hit_log（本地 SQLite 运营统计）的分工：统计聚合在本地 SQLite 就够；
审计要求逐轮原文可查、可按会话/用户追溯，落共享业务库由 Java 管理端直读。

两条铁律：
- 旁路语义——落库失败只记日志，绝不影响回复下发（审计不能比业务还金贵）；
- 自建会话——不复用请求的 session，fire-and-forget 场景下请求会话可能已关闭。
"""
import logging

from app.db import Base, SessionLocal, engine
from app.models.chat_log import ChatLog

logger = logging.getLogger("agsell.ai.chat_audit")

_ensured = False


def _ensure_table() -> None:
    """建表（幂等，进程内只跑一次）：本地开发库未跑 migration 时兜底"""
    global _ensured
    if _ensured:
        return
    Base.metadata.create_all(bind=engine, tables=[ChatLog.__table__])
    _ensured = True


def record_turn(
    user_id: int | None,
    session_id: str,
    question: str,
    reply: str,
    intent: str = "",
    tools: list[str] | None = None,
    refused: bool = False,
    hits: int = 0,
    refs: list[dict] | list[int] | None = None,
    duration_ms: int | None = None,
) -> None:
    """写一轮对话审计。任何异常都吞掉只记日志。"""
    try:
        _ensure_table()
        ref_ids = []
        for r in refs or []:
            if isinstance(r, dict):
                if r.get("id") is not None:
                    ref_ids.append(str(r["id"]))
            elif r is not None:
                ref_ids.append(str(r))
        session = SessionLocal()
        try:
            # 截 16000 字符：TEXT 列上限 65535 字节，utf8mb4 最坏 4 字节/字符（emoji 密集
            # 的回复可顶满），超限整行插入失败——审计是旁路，丢行比丢字节更伤
            session.add(ChatLog(
                user_id=user_id,
                session_id=(session_id or "")[:64],
                question=(question or "")[:16000],
                reply=(reply or "")[:16000],
                intent=(intent or "")[:32] or None,
                tools=",".join(tools or [])[:255] or None,
                refused=1 if refused else 0,
                hits=max(0, int(hits or 0)),
                refs=",".join(ref_ids)[:1000] or None,
                duration_ms=duration_ms,
            ))
            session.commit()
        finally:
            session.close()
    except Exception:
        logger.exception("对话审计落库失败（不影响回复）")
