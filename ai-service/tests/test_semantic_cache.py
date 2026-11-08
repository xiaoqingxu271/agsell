"""语义缓存（app/semantic_cache.py）单测：受控向量，不依赖 FastEmbed/数据库"""
import sqlite3
import time

import numpy as np
import pytest

from app import semantic_cache


def unit_vec(dim: int, idx: int) -> np.ndarray:
    """第 idx 维为 1 的单位向量：完全同向=1.0，正交=0.0，可控构造相似度"""
    v = np.zeros(dim, dtype=np.float32)
    v[idx] = 1.0
    return v


def cos(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


def test_store_then_lookup_exact_hit(tmp_path):
    db = str(tmp_path / "cache.db")
    vec = unit_vec(8, 0)
    semantic_cache.store(db, "运费怎么算", vec, "满 99 元包邮…", [{"id": 9, "question": "运费规则"}])

    hit = semantic_cache.lookup(db, unit_vec(8, 0), threshold=0.93, ttl_days=7)
    assert hit is not None
    assert hit["answer"] == "满 99 元包邮…"
    assert hit["references"] == [{"id": 9, "question": "运费规则"}]
    assert hit["query_text"] == "运费怎么算"
    assert hit["score"] == pytest.approx(1.0, abs=1e-5)


def test_lookup_miss_below_threshold(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "运费怎么算", unit_vec(8, 0), "A", [])

    # 正交向量相似度 0，远低于阈值
    assert semantic_cache.lookup(db, unit_vec(8, 1), threshold=0.93, ttl_days=7) is None


def test_threshold_boundary(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "q", unit_vec(8, 0), "A", [])
    # 与条目夹角构造相似度 ~0.95：命中 0.93、不命中 0.97
    mixed = unit_vec(8, 0) + 0.33 * unit_vec(8, 1)
    score = cos(mixed, unit_vec(8, 0))
    assert 0.93 < score < 0.97

    assert semantic_cache.lookup(db, mixed, threshold=0.93, ttl_days=7) is not None
    assert semantic_cache.lookup(db, mixed, threshold=0.97, ttl_days=7) is None


def test_lookup_returns_best_match(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "运费", unit_vec(8, 0), "运费答案", [])
    semantic_cache.store(db, "退款", unit_vec(8, 1), "退款答案", [])

    q = unit_vec(8, 1) + 0.1 * unit_vec(8, 0)
    hit = semantic_cache.lookup(db, q, threshold=0.5, ttl_days=7)
    assert hit is not None
    assert hit["answer"] == "退款答案"


def test_ttl_excludes_expired_rows(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "旧问句", unit_vec(8, 0), "旧答案", [])
    # 手工把 created_at 拨回 8 天前（超出 7 天 TTL）
    with sqlite3.connect(db) as conn:
        conn.execute("UPDATE semantic_cache SET created_at = ?", (time.time() - 8 * 86400,))

    assert semantic_cache.lookup(db, unit_vec(8, 0), threshold=0.5, ttl_days=7) is None


def test_clear_and_count(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "a", unit_vec(8, 0), "A", [])
    semantic_cache.store(db, "b", unit_vec(8, 1), "B", [])
    assert semantic_cache.count(db) == 2

    assert semantic_cache.clear(db) == 2
    assert semantic_cache.count(db) == 0
    assert semantic_cache.lookup(db, unit_vec(8, 0), threshold=0.1, ttl_days=7) is None


def test_fail_open_on_corrupted_db(tmp_path):
    """缓存库损坏：lookup 按 None 处理（fail-open），绝不让缓存故障拖垮对话"""
    db = tmp_path / "broken.db"
    db.write_bytes(b"this is not a sqlite file")
    assert semantic_cache.lookup(str(db), unit_vec(8, 0), threshold=0.1, ttl_days=7) is None
    assert semantic_cache.count(str(db)) == -1


def test_zero_vector_is_miss(tmp_path):
    db = str(tmp_path / "cache.db")
    semantic_cache.store(db, "q", unit_vec(8, 0), "A", [])
    assert semantic_cache.lookup(db, np.zeros(8, dtype=np.float32), threshold=0.1, ttl_days=7) is None


def test_store_failure_does_not_raise(tmp_path, monkeypatch):
    """写入失败只记日志不抛出（缓存是优化不是依赖）"""
    db = str(tmp_path / "x" / "forbidden" / "c.db")  # 不存在的目录
    semantic_cache.store(db, "q", unit_vec(8, 0), "A", [])  # 不应抛异常
