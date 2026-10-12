"""商品向量召回索引（collection="product"，与 FAQ 知识库分库互不污染）

复用 VectorStore 的条目结构 {id, question, answer, category, keywords}：
- question = 商品名（召回结果标签与 metadata 透传复用同一字段）
- answer   = 卖点文本（副标题 + 产地 + 储存/保质期）
- category = 商品分类名

MySQL product 表是数据源（source of truth），本索引只管召回商品 id，
价格/在售状态过滤与口碑注入由 Java 主系统 /ai/internal/product/search 完成。
"""
from sqlalchemy.orm import Session

from app.config import settings
from app.rag.vectorstore import VectorStore
from app.repositories import product_repo

PRODUCT_COLLECTION = "product"


def build_entry(p, category_name: str) -> dict:
    """商品行 → 向量索引条目"""
    parts = [p.subtitle or ""]
    if p.origin:
        parts.append(f"产地：{p.origin}")
    if category_name:
        parts.append(f"分类：{category_name}")
    if p.storage:
        parts.append(f"储存：{p.storage}")
    if p.shelf_life:
        parts.append(f"保质期：{p.shelf_life}")
    keywords = ",".join(x for x in (category_name, p.origin or "") if x)
    return {
        "id": p.id,
        "question": p.name,
        "answer": "｜".join(x for x in parts if x),
        "category": category_name,
        "keywords": keywords,
    }


class ProductStore:
    def __init__(self, vector_store: VectorStore):
        self._vs = vector_store

    def rebuild(self, session: Session) -> int:
        """全量重建商品索引：读在售商品 + 分类名 → 向量化 → 写 Chroma(product)"""
        cats = product_repo.category_name_map(session)
        entries = [build_entry(p, cats.get(p.category_id, "")) for p in product_repo.list_on_sale(session)]
        return self._vs.rebuild(entries)

    def search(self, question: str, top_k: int | None = None) -> list[dict]:
        """语义召回 [{id, score}]（相似度降序）；索引为空返回 []"""
        k = top_k or settings.PRODUCT_TOP_K
        return [{"id": hit["id"], "score": hit["score"]} for hit in self._vs.query(question, top_k=k)]

    def search_ids(self, question: str, top_k: int | None = None) -> list[int]:
        """语义召回商品 id（相似度降序）；索引为空返回 []"""
        return [h["id"] for h in self.search(question, top_k=top_k)]

    def count(self) -> int:
        return self._vs.count()
