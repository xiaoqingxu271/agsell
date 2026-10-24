"""会话记忆维护：thread 最近活跃记录 + 过期会话清理

工程债背景（评估清单 §4 第 5 条）：LangGraph 的 SQLite checkpointer 只写不删，
state.messages 随会话无限增长，长期运行 checkpoints.db 膨胀；且「演示前必须看
日志确认后端」——降级到 MemorySaver（重启丢记忆）时没有任何可观测信号。

方案（不依赖 langgraph 内部 id 语义，跨版本安全）：
- 本模块自维护 agsess_thread_seen(thread_id, updated_at) 表，每轮对话 best-effort
  刷新（一次 sub-ms 的 INSERT OR REPLACE，走工作线程不阻塞事件循环）；
- 清理任务在服务启动时与每 CHECKPOINT 清理周期各跑一次：
  ① 祖册（grandfather）：历史遗留 thread 首次登记为"现在仍活跃"，宽限一个 TTL 周期，
    避免升级当天就把 144 个存量会话全清掉；
  ② 删除 updated_at 早于 TTL 的 thread 在 checkpoints / writes / seen 三张表的全部行。
- 所有写入尽力而为：维护失败只记日志，绝不影响对话主链路。
"""
import logging
import sqlite3
import time
from datetime import datetime, timedelta, timezone

logger = logging.getLogger("agsell.ai.checkpoint_maintenance")

SEEN_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS agsess_thread_seen (
    thread_id  TEXT PRIMARY KEY,
    updated_at REAL NOT NULL
)
"""


def _connect(sqlite_path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(sqlite_path, timeout=10)
    conn.execute(SEEN_TABLE_SQL)
    return conn


def remember_thread(sqlite_path: str, thread_id: str) -> None:
    """登记 thread 最近活跃（同步阻塞，调用方放工作线程）。失败只记日志。"""
    try:
        with _connect(sqlite_path) as conn:
            conn.execute(
                "INSERT OR REPLACE INTO agsess_thread_seen(thread_id, updated_at) VALUES (?, ?)",
                (thread_id, time.time()),
            )
            conn.commit()
    except Exception:
        logger.warning("thread 活跃登记失败（忽略）：%s", thread_id, exc_info=True)


def cleanup_expired(sqlite_path: str, ttl_days: int, now: float | None = None) -> int:
    """删除超过 TTL 未活跃的会话在 checkpoints / writes / seen 三表的全部行。

    返回清理的 thread 数。ttl_days <= 0 时不清理（显式关闭）。
    """
    if ttl_days <= 0:
        return 0
    now_ts = now if now is not None else time.time()
    cutoff = now_ts - ttl_days * 86400
    conn = _connect(sqlite_path)
    try:
        cur = conn.cursor()
        # ① 祖册：升级部署前的存量 thread 没有 seen 记录，登记为"现在仍活跃"，
        #    宽限一个 TTL 周期后再按正常规则清理
        cur.execute(
            """
            INSERT OR IGNORE INTO agsess_thread_seen(thread_id, updated_at)
            SELECT DISTINCT thread_id, ? FROM checkpoints
            WHERE thread_id NOT IN (SELECT thread_id FROM agsess_thread_seen)
            """,
            (now_ts,),
        )
        conn.commit()
        # ② 找过期 thread
        expired = [r[0] for r in cur.execute(
            "SELECT thread_id FROM agsess_thread_seen WHERE updated_at < ?", (cutoff,)
        ).fetchall()]
        if not expired:
            return 0
        placeholders = ",".join("?" * len(expired))
        for table in ("checkpoints", "writes"):
            cur.execute(f"DELETE FROM {table} WHERE thread_id IN ({placeholders})", expired)
        cur.execute(f"DELETE FROM agsess_thread_seen WHERE thread_id IN ({placeholders})", expired)
        conn.commit()
        logger.info("checkpoint TTL 清理：%s 个超过 %s 天未活跃的会话", len(expired), ttl_days)
        return len(expired)
    finally:
        conn.close()


def last_seen_of(sqlite_path: str, thread_id: str) -> datetime | None:
    """测试与排障辅助：查某 thread 的最近活跃时间"""
    try:
        with _connect(sqlite_path) as conn:
            row = conn.execute(
                "SELECT updated_at FROM agsess_thread_seen WHERE thread_id = ?", (thread_id,)
            ).fetchone()
            return datetime.fromtimestamp(row[0], tz=timezone.utc) if row else None
    except Exception:
        return None


def stale_after(seconds: float) -> float:
    """测试辅助：生成一个"seconds 秒之前"的时间戳"""
    return time.time() - seconds
