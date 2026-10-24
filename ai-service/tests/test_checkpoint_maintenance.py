"""会话记忆维护测试：TTL 清理算法与活跃登记（临时 sqlite，不打真 checkpoints.db）

评估清单 §4 第 5 条工程债的收口：checkpoints/writes 只写不删、无限增长。
钉住四件事：
1. 过期 thread 在 checkpoints / writes / seen 三表一并清掉，未过期的不动；
2. 祖册（grandfather）：无 seen 记录的存量 thread 首轮宽限，不升级当天清库；
3. remember_thread 幂等刷新活跃时间，活跃中的会话不会被清理；
4. ttl_days=0 显式关闭。
"""
import sqlite3
import time

from app import checkpoint_maintenance as cm


def _mk_db(tmp_path):
    """仿 langgraph sqlite saver 的表结构 + 预置数据"""
    db = tmp_path / "checkpoints.db"
    conn = sqlite3.connect(db)
    conn.executescript(
        """
        CREATE TABLE checkpoints (
            thread_id TEXT, checkpoint_ns TEXT, checkpoint_id TEXT,
            parent_checkpoint_id TEXT, type TEXT, checkpoint BLOB, metadata TEXT
        );
        CREATE TABLE writes (
            thread_id TEXT, checkpoint_ns TEXT, checkpoint_id TEXT,
            task_id TEXT, idx INT, channel TEXT, type TEXT, value BLOB, task_path TEXT
        );
        """
    )
    for tid in ("old-thread", "fresh-thread", "legacy-thread"):
        conn.execute(
            "INSERT INTO checkpoints VALUES (?, '', 'cid-1', NULL, 'json', '{}', '{}')", (tid,)
        )
        conn.execute(
            "INSERT INTO writes VALUES (?, '', 'cid-1', 't1', 0, 'messages', 'json', '{}', '')", (tid,)
        )
    conn.commit()
    conn.close()
    return db


def test_cleanup_removes_only_expired_threads(tmp_path):
    db = str(_mk_db(tmp_path))
    cm.remember_thread(db, "old-thread")
    cm.remember_thread(db, "fresh-thread")
    # old-thread 活跃时间是 8 天前（TTL 7 天）
    with sqlite3.connect(db) as conn:
        conn.execute("UPDATE agsess_thread_seen SET updated_at = ? WHERE thread_id = 'old-thread'",
                     (time.time() - 8 * 86400,))
        conn.commit()

    deleted = cm.cleanup_expired(db, ttl_days=7)

    assert deleted == 1
    conn = sqlite3.connect(db)
    left_checkpoints = {r[0] for r in conn.execute("SELECT DISTINCT thread_id FROM checkpoints")}
    left_writes = {r[0] for r in conn.execute("SELECT DISTINCT thread_id FROM writes")}
    left_seen = {r[0] for r in conn.execute("SELECT thread_id FROM agsess_thread_seen")}
    conn.close()
    # old-thread 三表全清；fresh-thread 与 legacy-thread（祖册宽限）保留
    assert left_checkpoints == {"fresh-thread", "legacy-thread"}
    assert left_writes == {"fresh-thread", "legacy-thread"}
    assert left_seen == {"fresh-thread", "legacy-thread"}


def test_grandfather_protects_existing_threads_on_first_run(tmp_path):
    """存量 thread 无 seen 记录：首轮清理先祖册宽限，不误删"""
    db = str(_mk_db(tmp_path))  # 三个 thread 都没有 seen 记录
    deleted = cm.cleanup_expired(db, ttl_days=1)
    assert deleted == 0
    conn = sqlite3.connect(db)
    n = conn.execute("SELECT COUNT(DISTINCT thread_id) FROM checkpoints").fetchone()[0]
    conn.close()
    assert n == 3


def test_remember_thread_refreshes_activity(tmp_path):
    db = str(_mk_db(tmp_path))
    cm.remember_thread(db, "old-thread")
    with sqlite3.connect(db) as conn:
        conn.execute("UPDATE agsess_thread_seen SET updated_at = ? WHERE thread_id = 'old-thread'",
                     (time.time() - 9 * 86400,))
        conn.commit()
    # 用户回来继续聊：登记刷新活跃时间，清理时不再过期
    cm.remember_thread(db, "old-thread")
    deleted = cm.cleanup_expired(db, ttl_days=7)
    assert deleted == 0
    assert cm.last_seen_of(db, "old-thread") is not None


def test_ttl_zero_disables_cleanup(tmp_path):
    db = str(_mk_db(tmp_path))
    cm.remember_thread(db, "old-thread")
    with sqlite3.connect(db) as conn:
        conn.execute("UPDATE agsess_thread_seen SET updated_at = 0")
        conn.commit()
    assert cm.cleanup_expired(db, ttl_days=0) == 0
