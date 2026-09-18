"""Chroma 向量库封装（bge-m3 via FastEmbed）

- 数据落本地目录（VECTOR_DB_PATH），重启不丢
- 显式传入 embedding，避免 Chroma 内置默认模型下载
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
        self._collection = self._client.get_or_create_collection(
            name=COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
        )
        self._embedder = embedder or TextEmbedding(self._model)

    # ---- 索引侧 ----
    def rebuild(self, entries: list[dict]) -> int:
        """全量重建：清空集合后写入全部条目。entries 元素：{id, question, answer, category, keywords}"""
        self._client.delete_collection(COLLECTION_NAME)
        self._collection = self._client.create_collection(
            name=COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
        )
        if not entries:
            return 0
        ids = [str(e["id"]) for e in entries]
        docs = [f"{e['question']}\n{e['answer']}" for e in entries]
        metadatas = [
            {
                "category": e.get("category", ""),
                "keywords": e.get("keywords", ""),
                "question": e["question"],
            }
            for e in entries
        ]
        embeddings = [v.tolist() for v in self._embedder.embed(docs)]
        self._collection.add(ids=ids, documents=docs, embeddings=embeddings, metadatas=metadatas)
        return len(ids)

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
