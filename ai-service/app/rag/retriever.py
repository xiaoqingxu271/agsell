"""双路召回检索器：bge 向量召回 + FAQ 关键词精确命中，合并去重

反幻觉闸门：向量召回得分低于 KB_SCORE_THRESHOLD 的条目不返回（域外/弱相关问题
整体被拒之门外，交给生成节点的确定性拒答话术），关键词精确命中不受阈值限制。
"""

from app.config import settings
from app.db import SessionLocal
from app.rag.vectorstore import VectorStore


class Retriever:
    def __init__(
        self,
        vector_store: VectorStore,
        faq_repo,
        session_factory=None,
        top_k: int | None = None,
        score_threshold: float | None = None,
    ):
        self._vs = vector_store
        self._faq_repo = faq_repo
        self._session_factory = session_factory or SessionLocal
        self._top_k = top_k or settings.KB_TOP_K
        self._threshold = settings.KB_SCORE_THRESHOLD if score_threshold is None else score_threshold

    def retrieve(self, question: str, top_k: int | None = None) -> list[dict]:
        """返回 [{id, question, answer, score}]，按综合得分降序，去重。

        向量召回得分 < score_threshold 的条目被过滤（0 或负数 = 关闭阈值）；
        关键词精确命中的条目始终保留。
        """
        k = top_k or self._top_k
        merged: dict[int, dict] = {}

        # 1. 向量召回（语义相似）
        for hit in self._vs.query(question, top_k=k * 2):
            merged[hit["id"]] = {"id": hit["id"], "question": hit["question"], "score": hit["score"]}

        # 2. 关键词召回（精确命中，加权重）
        with self._session_factory() as session:
            faqs = self._faq_repo.list_enabled(session)
            answers = {f.id: f.answer for f in faqs}
            for faq in faqs:
                kw_list = [x.strip() for x in (faq.keywords or "").split(",") if x.strip()]
                matched = [kw for kw in kw_list if kw and kw in question]
                if not matched:
                    continue
                item = merged.get(faq.id)
                if item:
                    item["score"] = round(item["score"] + min(len(matched), 3) * 0.5, 4)
                    item["keyword_hit"] = True
                else:
                    merged[faq.id] = {
                        "id": faq.id,
                        "question": faq.question,
                        "score": min(len(matched), 3) * 0.5,
                        "keyword_hit": True,
                    }

        # 3. 回填答案（只保留有答案的条目）
        results = []
        for item in merged.values():
            answer = answers.get(item["id"])
            if answer is None:
                continue
            results.append({**item, "answer": answer})

        # 4. 反幻觉闸门：过滤低分向量召回；关键词精确命中不受限制
        if self._threshold and self._threshold > 0:
            results = [r for r in results if r.get("keyword_hit") or r["score"] >= self._threshold]

        results.sort(key=lambda x: x["score"], reverse=True)
        return results[:k]
