"""Chroma 向量库封装（bge-small-zh via FastEmbed）

- 数据落本地目录（VECTOR_DB_PATH），重启不丢
- 显式传入 embedding，避免 Chroma 内置默认模型下载
- 全量重建（rebuild）与单条增量（upsert / delete_ids）并存：
  FAQ 新增/修改/删除后走增量同步，无需每次全量重灌。
"""
import chromadb
from fastembed import TextEmbedding

from app.config import settings

COLLECTION_NAME = "faq"


class VectorStore:
    def __init__(self, path: str | None = None, model: str | None = None, embedder=None):
        self._path = path or settings.VECTOR_DB_PATH
        self._model = model or settings.EMBEDDING_MODEL
        self._client = chromadb.PersistentClient(path=self._path)
        # 固定 cosine；delete_collection 后需按相同配置重建集合
        self._collection_config = {"hnsw:space": "cosine"}
        self._collection = self._client.get_or_create_collection(
            name=COLLECTION_NAME, metadata=self._collection_config
        )
        self._embedder = embedder or TextEmbedding(self._model)

    # ---- 记录构造（rebuild / upsert 共用，保证文档与元数据口径一致）----
    @staticmethod
    def _doc_text(entry: dict) -> str:
        return f"{entry['question']}\n{entry['answer']}"

    @staticmethod
    def _metadata(entry: dict) -> dict:
        return {
            "category": entry.get("category", ""),
            "keywords": entry.get("keywords", ""),
            "question": entry["question"],
        }

    def _build_records(self, entries: list[dict]):
        ids = [str(e["id"]) for e in entries]
        docs = [self._doc_text(e) for e in entries]
        metadatas = [self._metadata(e) for e in entries]
        embeddings = [v.tolist() for v in self._embedder.embed(docs)]
        return ids, docs, metadatas, embeddings

    # ---- 索引侧 ----
    def rebuild(self, entries: list[dict]) -> int:
        """全量重建：清空集合后写入全部条目。entries 元素：{id, question, answer, category, keywords}"""
        self._client.delete_collection(COLLECTION_NAME)
        self._collection = self._client.create_collection(
            name=COLLECTION_NAME, metadata=self._collection_config
        )
        if not entries:
            return 0
        ids, docs, metadatas, embeddings = self._build_records(entries)
        self._collection.add(ids=ids, documents=docs, embeddings=embeddings, metadatas=metadatas)
        return len(ids)

    def upsert(self, entry: dict) -> int:
        """单条增量写入/更新（存在则覆盖，不存在则插入）。返回写入条数（0/1）。"""
        return self.upsert_many([entry])

    def upsert_many(self, entries: list[dict]) -> int:
        """批量增量写入/更新。"""
        if not entries:
            return 0  # 空输入直接返回，不触发 embedding
        ids, docs, metadatas, embeddings = self._build_records(entries)
        self._collection.upsert(ids=ids, documents=docs, embeddings=embeddings, metadatas=metadatas)
        return len(ids)

    def delete_ids(self, ids) -> int:
        """按主键从索引删除，返回实际删除条数；不存在的 id 会被忽略（不报错）。"""
        sids = [str(i) for i in ids if i is not None]
        if not sids:
            return 0
        # 先查实际存在的 id，避免 Chroma 对不存在 id 产生告警/异常
        existing = set(self._collection.get(ids=sids).get("ids") or [])
        valid = [s for s in sids if s in existing]
        if valid:
            self._collection.delete(ids=valid)
        return len(valid)

    def count(self) -> int:
        return self._collection.count()

    # ---- 检索侧 ----
    def query(self, question: str, top_k: int = 5) -> list[dict]:
        """向量召回：返回 [{id, question, score}]，score 为余弦相似度（越大越相关）"""
        if self._collection.count() == 0:
            return []
        emb = list(self._embedder.query_embed(question))[0].tolist()
        res = self._collection.query(
            query_embeddings=[emb], n_results=top_k, include=["documents", "metadatas", "distances"]
        )
        hits = []
        ids = res.get("ids", [[]])[0]
        metas = res.get("metadatas", [[]])[0]
        dists = res.get("distances", [[]])[0]
        for cid, meta, dist in zip(ids, metas, dists):
            hits.append(
                {
                    "id": int(cid),
                    "question": (meta or {}).get("question", ""),
                    "score": round(1.0 - float(dist), 4),  # 余弦距离 → 相似度
                }
            )
        return hits
