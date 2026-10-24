"""知识库引用日志测试（运营视图数据源，临时 sqlite 不打真库）"""
import time

from app import kb_hit_log


def test_log_and_recent_top_roundtrip(tmp_path):
    db = str(tmp_path / "kb_hits.db")
    refs = [{"id": 19, "question": "运费怎么计算？"}, {"id": 19, "question": "运费怎么计算？"},
            {"id": 35, "question": "首页有什么功能？"}]
    assert kb_hit_log.log_kb_hits(db, "t1", refs) == 3

    top = kb_hit_log.recent_top(db, days=7, limit=10)
    assert top[0] == {"faqId": 19, "question": "运费怎么计算？", "hits": 2}
    assert top[1]["faqId"] == 35


def test_log_empty_or_invalid_references_is_noop(tmp_path):
    db = str(tmp_path / "kb_hits.db")
    assert kb_hit_log.log_kb_hits(db, "t1", []) == 0
    assert kb_hit_log.log_kb_hits(db, "t1", [None, "bad", {}]) == 0
    assert kb_hit_log.recent_top(db) == []


def test_recent_top_respects_days_window(tmp_path):
    db = str(tmp_path / "kb_hits.db")
    kb_hit_log.log_kb_hits(db, "t1", [{"id": 19, "question": "旧引用"}])
    # 把唯一一条记录改成 8 天前：7 日窗口内应为空
    with __import__("sqlite3").connect(db) as conn:
        conn.execute("UPDATE kb_hit_log SET ts = ?", (time.time() - 8 * 86400,))
        conn.commit()
    assert kb_hit_log.recent_top(db, days=7) == []


def test_recent_top_on_missing_db_returns_empty(tmp_path):
    assert kb_hit_log.recent_top(str(tmp_path / "nope.db")) == []
