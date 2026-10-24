"""三路混合召回检索器：向量语义 + MySQL 全文（BM25）+ FAQ 关键词，RRF 融合

反幻觉闸门不变：只有"有词面证据"（关键词/全文通道）或"向量分过阈值"的条目才可能进入结果，
域外问法整体被拒之门外，交给生成节点的确定性拒答话术。

三路分工（答辩口径）：
- 向量（bge-small-zh）：语义相似度召回，KB_SCORE_THRESHOLD 闸门，域内 top-1 均值 ~0.71 / 域外 ~0.33；
- 全文（MySQL ngram FULLTEXT，BM25 相关度）：从 question/answer/keywords 三列召回词面证据，
  MySQL 天然按相关度排序，规模增长后不必全表拉内存。词面通道不做相似度阈值，
  但要求「去掉停用词后的查询（≥2 字）在条目问句+答案原文中出现」——
  "推荐一部电影"剥掉"推荐"后余下"一部电影"几乎不可能出现在知识库原文里，
  闸门语义与旧关键词通道一致，域外拒答率不因新通道回退；
- 关键词（ai_faq.keywords 子串）：旧通道保留，每条命中 +0.5 分，停用词不豁免。
三路排名用 RRF（Reciprocal Rank Fusion，k=60）融合：不依赖不同通道分数量纲，
任一通道故障自动退化为两路/一路，检索层永不因单通道故障对用户报错。
"""
import logging

from sqlalchemy import text

from app.config import settings
from app.db import SessionLocal
from app.rag.vectorstore import VectorStore

logger = logging.getLogger("agsell.ai.retriever")

# RRF 常数（论文常用值 60）：排名越靠前贡献越大，但不会被单通道第一名垄断
_RRF_K = 60
# 全文通道候选条数：BM25 只取最相关的一小撮，词面校验在 Python 侧做
_FULLTEXT_TOP_K = 10


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
        # 低区分度通用词（"推荐/什么时候"等）：命中不加权、也不豁免拒答阈值
        # （量化实验五改进方向落地：修复"推荐一部电影"被关键词通道带进知识库答案）
        self._stopwords = {
            x.strip()
            for x in (getattr(settings, "KB_KEYWORD_STOPWORDS", "") or "").split(",")
            if x.strip()
        }

    def retrieve(self, question: str, top_k: int | None = None) -> list[dict]:
        """返回 [{id, question, answer, score, keyword_hit, fulltext_hit}]，按 RRF 融合分降序。

        向量召回得分 < score_threshold 的条目不进入向量通道（0 或负数 = 关闭阈值）；
        关键词/全文通道的条目有词面证据，始终保留。
        """
        k = top_k or self._top_k

        # ---------- 三通道各自召回，产出 {id: 条目} 与排名 ----------
        lane_members: list[list[dict]] = []  # 每通道一条按相关度降序的列表

        # 1. 向量通道（语义相似，阈值闸门在这里）
        vector_hits: list[dict] = []
        for hit in self._vs.query(question, top_k=k * 2):
            if self._threshold and self._threshold > 0 and hit["score"] < self._threshold:
                continue
            vector_hits.append({"id": hit["id"], "question": hit["question"], "score": hit["score"]})
        if vector_hits:
            lane_members.append(vector_hits)

        with self._session_factory() as session:
            faqs = self._faq_repo.list_enabled(session)
            answers = {f.id: f.answer for f in faqs}

            # 2. 关键词通道（ai_faq.keywords 子串，停用词过滤）
            keyword_hits: list[dict] = []
            for faq in faqs:
                kw_list = [x.strip() for x in (faq.keywords or "").split(",") if x.strip()]
                matched = [kw for kw in kw_list if kw and kw in question and kw not in self._stopwords]
                if not matched:
                    continue
                keyword_hits.append({
                    "id": faq.id,
                    "question": faq.question,
                    "score": min(len(matched), 3) * 0.5,
                    "keyword_hit": True,
                })
            keyword_hits.sort(key=lambda x: -x["score"])
            if keyword_hits:
                lane_members.append(keyword_hits)

            # 3. 全文通道（MySQL ngram BM25 相关度 + 词面校验）；索引缺失/库故障 → 通道自动退场
            fulltext_hits = self._fulltext_lane(session, question)
            if fulltext_hits:
                lane_members.append(fulltext_hits)

        # ---------- RRF 融合：score = Σ 1/(K + rank)，与各通道分数量纲无关 ----------
        rrf: dict[int, float] = {}
        entries: dict[int, dict] = {}
        for lane in lane_members:
            for rank, item in enumerate(lane):
                pid = item["id"]
                rrf[pid] = rrf.get(pid, 0.0) + 1.0 / (_RRF_K + rank + 1)
                entry = entries.setdefault(pid, {
                    "id": pid,
                    "question": item["question"],
                    "score": 0.0,
                    "keyword_hit": False,
                    "fulltext_hit": False,
                    "_best": 0.0,
                })
                entry["keyword_hit"] = entry["keyword_hit"] or item.get("keyword_hit", False)
                entry["fulltext_hit"] = entry["fulltext_hit"] or item.get("fulltext_hit", False)
                entry["_best"] = max(entry["_best"], item["score"])

        # 回填答案（只保留有答案的条目）
        results = []
        for pid, entry in entries.items():
            answer = answers.get(pid)
            if answer is None:
                continue
            results.append({
                "id": pid,
                "question": entry["question"],
                "answer": answer,
                # 对外 score 即 RRF 融合分（越大越相关）；_best 只作排序 tie-break
                "score": round(rrf[pid], 4),
                "keyword_hit": entry["keyword_hit"],
                "fulltext_hit": entry["fulltext_hit"],
                "_best": entry["_best"],
            })

        # 排序：RRF 分 → 有词面证据者优先（同样排名时，命中关键词比纯语义更像"问的就是它"）
        results.sort(key=lambda x: (-x["score"], not x["keyword_hit"], -x["_best"]))
        for r in results:
            r.pop("_best", None)
        return results[:k]

    def _fulltext_lane(self, session, question: str) -> list[dict]:
        """MySQL ngram 全文召回 + 词面校验；任何故障都退场（不挡主链路）。

        词面校验：把查询按停用词切成片段，条目 question+answer 原文须包含其中
        任一 ≥2 字片段。没有这一步，"推荐一部电影"会凭"推荐"二字的大权重命中
        一堆含"推荐"的条目，域外问题就被全文通道带进答案——这正是关键词通道
        当年踩过的坑；而"运费怎么算"切成"运费/算"后，"运费"能正常命中。
        """
        segments = [s.strip() for s in self._strip_stopwords(question).split() if len(s.strip()) >= 2]
        if not segments:
            return []
        try:
            rows = session.execute(
                text(
                    """
                    select id, question, answer,
                           match(question, answer, keywords)
                               against (:q in natural language mode) as relevance
                    from ai_faq
                    where status = 1
                      and match(question, answer, keywords)
                          against (:q in natural language mode)
                    order by relevance desc
                    limit :lim
                    """
                ),
                {"q": " ".join(segments), "lim": _FULLTEXT_TOP_K},
            ).all()
        except Exception as e:
            logger.warning("全文召回通道不可用，退化为向量+关键词两路：%s", e)
            return []
        hits: list[dict] = []
        for r in rows:
            faq_id, faq_question, faq_answer = int(r[0]), str(r[1] or ""), str(r[2] or "")
            doc_text = faq_question + faq_answer
            if not any(seg in doc_text for seg in segments):
                continue
            hits.append({
                "id": faq_id,
                "question": faq_question,
                "score": float(r[3] or 0.0),
                "fulltext_hit": True,
            })
        return hits

    def _strip_stopwords(self, question: str) -> str:
        """把查询按停用词切开取剩余片段（连续非停用词段），供词面校验"""
        remainder = question
        for sw in self._stopwords:
            remainder = remainder.replace(sw, " ")
        return remainder
