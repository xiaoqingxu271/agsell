"""FAQ 语义缓存（高频同义问法直接复用上一轮 KB 答案）

动机：FAQ 类问题（"运费怎么算"）每轮都全量走三路检索 + 两次串行 LLM 调用，
而平台规则的答案与用户身份无关、几乎不随时间变化。以查询向量 cosine 相似度
做键（阈值见 config.SEMANTIC_CACHE_THRESHOLD），命中即跳过整条 LangGraph 链路。

边界（宁缺勿滥，见 main.py 的可缓存判定）：
- 只缓存"纯知识问答轮"——工具只有 kb_search、无商品卡（rec_items 为空）、
  未触发转人工、未拒答；订单/物流/售后/导购轮带 userId 个性化或私有数据，永不入缓存；
- 命中的引用（references）随答案一并复用，前端"依据"标签口径不变；
- 知识库任何写操作（FAQ 增删改 / 全量重建 / 文档上传与删除）整库清空——缓存是
  派生数据的派生数据，宁可多算一次也不冒答旧答案的风险；
- TTL（默认 7 天）兜底防无限增长。

实现说明：SQLite 单表 + 全表扫描逐行算 cosine。毕设知识库规模（几百条缓存）
下 P50 亚毫秒级，不值得引入向量索引；规模上来后可平移到 Chroma 独立 collection。
任何数据库异常一律按未命中（fail-open），缓存故障绝不影响对话可用性。
"""
import json
import logging
import sqlite3
import time

import numpy as np

logger = logging.getLogger("agsell.ai")

_SCHEMA = """
CREATE TABLE IF NOT EXISTS semantic_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_text TEXT NOT NULL,
    embedding BLOB NOT NULL,
    answer TEXT NOT NULL,
    references_json TEXT NOT NULL,
    created_at REAL NOT NULL
)
"""


def _connect(db_path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path)
    conn.execute(_SCHEMA)
    return conn


def embed_query(embedder, text: str) -> np.ndarray:
    """FastEmbed 单条查询向量（float32；归一化推迟到 lookup，两侧统一口径）"""
    vec = next(iter(embedder.embed([text])))
    return np.asarray(vec, dtype=np.float32)


def store(db_path: str, query_text: str, embedding, answer: str, references: list) -> None:
    """写入一条缓存。失败只记日志（缓存是优化，不是依赖）。"""
    try:
        blob = np.asarray(embedding, dtype=np.float32).tobytes()
        with _connect(db_path) as conn:
            conn.execute(
                "INSERT INTO semantic_cache(query_text, embedding, answer, references_json, created_at)"
                " VALUES (?,?,?,?,?)",
                (query_text, blob, answer, json.dumps(references, ensure_ascii=False), time.time()),
            )
    except Exception:  # noqa: BLE001
        logger.warning("语义缓存写入失败（忽略）", exc_info=True)


def lookup(db_path: str, embedding, threshold: float, ttl_days: int) -> dict | None:
    """余弦相似度最高的条目达到阈值即命中。

    返回 {query_text, answer, references, score}；未命中 / 空库 / 任何异常 → None
    （调用方按未命中继续走全链路，绝不因缓存故障降级对话）。
    """
    try:
        vec = np.asarray(embedding, dtype=np.float32)
        vec_norm = float(np.linalg.norm(vec))
        if not vec.size or vec_norm == 0.0:
            return None
        cutoff = time.time() - max(ttl_days, 0) * 86400
        with _connect(db_path) as conn:
            rows = conn.execute(
                "SELECT query_text, embedding, answer, references_json"
                " FROM semantic_cache WHERE created_at >= ?",
                (cutoff,),
            ).fetchall()
        best: dict | None = None
        best_score = -1.0
        for qtext, blob, answer, refs_json in rows:
            cached = np.frombuffer(blob, dtype=np.float32)
            denom = float(np.linalg.norm(cached)) * vec_norm
            if denom == 0.0:
                continue
            score = float(np.dot(cached, vec) / denom)
            if score > best_score:
                best_score = score
                best = {
                    "query_text": qtext,
                    "answer": answer,
                    "references": json.loads(refs_json),
                    "score": round(score, 4),
                }
        if best and best_score >= threshold:
            return best
        return None
    except Exception:  # noqa: BLE001
        logger.warning("语义缓存查询失败（按未命中处理）", exc_info=True)
        return None


def clear(db_path: str) -> int:
    """整库清空（知识库写操作后调用）。返回删除条数，失败返回 -1（调用方只记日志）。"""
    try:
        with _connect(db_path) as conn:
            cur = conn.execute("DELETE FROM semantic_cache")
            return cur.rowcount
    except Exception:  # noqa: BLE001
        logger.warning("语义缓存清空失败（忽略）", exc_info=True)
        return -1


def count(db_path: str) -> int:
    """当前条数（运维/实验用），异常返回 -1。"""
    try:
        with _connect(db_path) as conn:
            return int(conn.execute("SELECT COUNT(*) FROM semantic_cache").fetchone()[0])
    except Exception:  # noqa: BLE001
        return -1
