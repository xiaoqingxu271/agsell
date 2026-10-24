"""知识库引用日志：哪条 FAQ 被 AI 引用过、近 7 日引用 Top（运营视图数据源）

背景（评估清单「运营视图」遗留项）：知识库只能看条目，看不到"哪条被用过"，
答辩演示"知识闭环"时讲不出引用侧的数据。

设计：
- 落 ai-service 自己的 data/kb_hits.db（SQLite），不碰业务库——AI 侧对 MySQL 只读、
  写只碰 ai_faq 的边界不破；
- 数据源是 state.references（生成节点的结构化引用，批次二已有），记录零额外检索成本；
- 写入尽力而为（工作线程 + 失败只记日志），统计失败返回空列表——运营视图是增益，
  不能让它拖垮 stats 接口。
"""
import logging
import sqlite3
import time

logger = logging.getLogger("agsell.ai.kb_hit_log")

_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS kb_hit_log (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    ts        REAL    NOT NULL,
    thread_id TEXT    NOT NULL,
    faq_id    INTEGER NOT NULL,
    question  TEXT    NOT NULL
)
"""

# 每轮对话一条（运营统计：对话量/工具分布/拒答率）。与引用日志同库，AI 自有数据不碰业务库。
_CHAT_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS chat_log (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    ts        REAL    NOT NULL,
    thread_id TEXT    NOT NULL,
    user_id   INTEGER,
    intent    TEXT    NOT NULL DEFAULT '',
    tools     TEXT    NOT NULL DEFAULT '[]',
    refused   INTEGER NOT NULL DEFAULT 0,
    has_items INTEGER NOT NULL DEFAULT 0
)
"""


def _connect(path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(path, timeout=10)
    conn.execute(_TABLE_SQL)
    conn.execute(_CHAT_TABLE_SQL)
    return conn


def log_kb_hits(path: str, thread_id: str, references: list) -> int:
    """把本轮 references（[{id, question}]）写入引用日志。失败只记日志，返回写入条数。"""
    refs = [r for r in (references or []) if isinstance(r, dict) and r.get("id")]
    if not refs:
        return 0
    try:
        now = time.time()
        with _connect(path) as conn:
            conn.executemany(
                "INSERT INTO kb_hit_log(ts, thread_id, faq_id, question) VALUES (?, ?, ?, ?)",
                [(now, thread_id, int(r["id"]), str(r.get("question") or "")) for r in refs],
            )
            conn.commit()
        return len(refs)
    except Exception:
        logger.warning("知识库引用日志写入失败（忽略）：thread=%s", thread_id, exc_info=True)
        return 0


def log_chat_turn(path: str, thread_id: str, user_id: int | None, turn: dict) -> bool:
    """登记一轮对话（运营统计）。turn: {intent, tools, refused, has_items}；失败只记日志。"""
    try:
        import json as _json

        with _connect(path) as conn:
            conn.execute(
                "INSERT INTO chat_log(ts, thread_id, user_id, intent, tools, refused, has_items)"
                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    time.time(), thread_id, user_id,
                    str(turn.get("intent") or ""),
                    _json.dumps(turn.get("tools") or [], ensure_ascii=False),
                    1 if turn.get("refused") else 0,
                    1 if turn.get("has_items") else 0,
                ),
            )
            conn.commit()
        return True
    except Exception:
        logger.warning("对话运营日志写入失败（忽略）：thread=%s", thread_id, exc_info=True)
        return False


def ops_summary(path: str, days: int = 7) -> dict:
    """近 N 日运营汇总：对话量 / 拒答率 / 工具分布 / 按日趋势；失败或无数据返回零值结构。"""
    cutoff = time.time() - days * 86400
    empty = {"days": days, "totalTurns": 0, "refusedTurns": 0, "refusalRate": 0.0,
             "byTool": {}, "daily": []}
    try:
        import json as _json
        from datetime import date, datetime, timedelta

        with _connect(path) as conn:
            total = conn.execute(
                "SELECT COUNT(*) FROM chat_log WHERE ts >= ?", (cutoff,)).fetchone()[0]
            if not total:
                return empty
            refused = conn.execute(
                "SELECT COUNT(*) FROM chat_log WHERE ts >= ? AND refused = 1", (cutoff,)).fetchone()[0]
            tool_counter: dict[str, int] = {}
            for (tools_json,) in conn.execute(
                    "SELECT tools FROM chat_log WHERE ts >= ?", (cutoff,)).fetchall():
                for t in _json.loads(tools_json or "[]"):
                    tool_counter[t] = tool_counter.get(t, 0) + 1
            by_tool = dict(sorted(tool_counter.items(), key=lambda kv: -kv[1]))
            # 按自然日趋势（近 days 天，缺数日补零）
            start_day = (datetime.now() - timedelta(days=days - 1)).date()
            rows = conn.execute(
                "SELECT ts FROM chat_log WHERE ts >= ?", (cutoff,)).fetchall()
            per_day: dict[str, int] = {}
            for (ts,) in rows:
                key = date.fromtimestamp(ts).isoformat()
                per_day[key] = per_day.get(key, 0) + 1
            daily = []
            for i in range(days):
                key = (start_day + timedelta(days=i)).isoformat()
                daily.append({"date": key, "count": per_day.get(key, 0)})
        return {
            "days": days,
            "totalTurns": int(total),
            "refusedTurns": int(refused),
            "refusalRate": round(refused / total, 4) if total else 0.0,
            "byTool": by_tool,
            "daily": daily,
        }
    except Exception:
        logger.warning("运营统计聚合失败（返回零值）", exc_info=True)
        return empty


def recent_top(path: str, days: int = 7, limit: int = 10) -> list[dict]:
    """近 N 日引用 Top：[{faq_id, question, hits}]，按引用次数降序；失败/无数据返回空列表"""
    cutoff = time.time() - days * 86400
    try:
        with _connect(path) as conn:
            rows = conn.execute(
                """
                select faq_id, max(question) as question, count(*) as hits
                from kb_hit_log
                where ts >= ?
                group by faq_id
                order by hits desc, faq_id asc
                limit ?
                """,
                (cutoff, limit),
            ).fetchall()
        return [{"faqId": int(r[0]), "question": r[1], "hits": int(r[2])} for r in rows]
    except Exception:
        logger.warning("知识库引用统计失败（返回空）", exc_info=True)
        return []
