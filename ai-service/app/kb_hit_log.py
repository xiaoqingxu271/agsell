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


def _connect(path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(path, timeout=10)
    conn.execute(_TABLE_SQL)
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
