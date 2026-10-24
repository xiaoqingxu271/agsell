"""三路混合召回测试：向量（阈值闸门）+ 全文 BM25（词面校验）+ 关键词，RRF 融合

第二批整改：关键词子串通道升级为 MySQL ngram 全文通道 + RRF 融合。
钉住四件事：
1. 域外拒答闸门语义不回退——全文通道必须过词面校验，"推荐一部电影"进不来；
2. 全文通道的词面校验按停用词切段、任一 ≥2 字段命中原文即放行（"运费怎么算"不误杀）；
3. 任一通道故障自动退场，检索永不因单通道故障报错；
4. RRF 融合排序：多通道共同命中的条目排前，并列时词面证据优先。
"""
import pytest

from app.rag.retriever import Retriever


class FakeVectorStore:
    def __init__(self, hits=None):
        self._hits = [{"id": 2, "question": "怎么退款？", "score": 0.85}] if hits is None else hits

    def query(self, question, top_k=5):
        return self._hits


class FakeFaqRepo:
    def __init__(self, faqs=None):
        self.faqs = faqs or [
            {"id": 1, "question": "怎么联系人工客服？", "answer": "电话 400", "keywords": "客服,电话"},
            {"id": 2, "question": "怎么退款？", "answer": "申请售后", "keywords": "退款,售后"},
        ]

    def list_enabled(self, session=None):
        return [type("F", (), f)() for f in self.faqs]


class _FakeResult:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class _FakeSession:
    """全文通道的假会话：execute 返回预置行（模拟 BM25 召回结果）"""

    def __init__(self, rows=None):
        self._rows = rows or []
        self.queries = []

    def execute(self, stmt, params=None):
        self.queries.append(params)
        return _FakeResult(self._rows)

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class _BrokenSession:
    """execute 一律抛异常：模拟全文索引缺失 / 数据库故障"""

    def execute(self, *a, **k):
        raise RuntimeError("fulltext index missing")

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def _retriever(vector_hits=None, faqs=None, session=None):
    return Retriever(
        FakeVectorStore(vector_hits), FakeFaqRepo(faqs),
        session_factory=(lambda: session) if session is not None else (lambda: _BrokenSession()),
    )


# ---------- 既有两路行为不回退 ----------

def test_dual_recall_merge():
    r = _retriever()
    hits = r.retrieve("怎么退款 客服")
    ids = [h["id"] for h in hits]
    assert 1 in ids and 2 in ids
    assert len(hits) == 2
    assert all("answer" in h for h in hits)
    # 双通道共同命中（向量+关键词）的 id=2 应排在仅关键词命中的 id=1 前（RRF 累加分更高）
    assert ids.index(2) < ids.index(1)


def test_keyword_only_recall():
    r = _retriever()
    hits = r.retrieve("客服电话是多少")
    assert hits[0]["id"] == 1


def test_threshold_filters_low_score_vector_hits():
    r = _retriever(vector_hits=[{"id": 2, "question": "怎么退款？", "score": 0.31}])
    assert r.retrieve("怎么看今天的股市行情") == []


def test_keyword_hit_survives_below_threshold():
    r = Retriever(
        FakeVectorStore(hits=[]), FakeFaqRepo(), top_k=5, score_threshold=0.9,
        session_factory=lambda: _BrokenSession())
    hits = r.retrieve("客服电话是多少")
    assert hits and hits[0]["id"] == 1 and hits[0]["keyword_hit"] is True


def test_threshold_disabled_when_zero():
    r = Retriever(
        FakeVectorStore(hits=[{"id": 2, "question": "怎么退款？", "score": 0.31}]),
        FakeFaqRepo(), top_k=5, score_threshold=0,
        session_factory=lambda: _BrokenSession())
    assert [h["id"] for h in r.retrieve("随便什么问题")] == [2]


def test_stopword_keyword_does_not_bypass_threshold():
    class Repo:
        def list_enabled(self, session=None):
            return [type("F", (), {"id": 9, "question": "首页有什么功能？",
                                   "answer": "轮播/分类/搜索", "keywords": "推荐,首页"})()]

    r = Retriever(FakeVectorStore(hits=[]), Repo(), top_k=5, score_threshold=0.5,
                  session_factory=lambda: _BrokenSession())
    assert r.retrieve("推荐一部好看的电影") == []
    hits = r.retrieve("首页都有什么")
    assert hits and hits[0]["id"] == 9 and hits[0]["keyword_hit"] is True


# ---------- 全文通道：词面校验与闸门语义 ----------

def test_fulltext_lane_admits_lexical_match():
    """BM25 召回的条目须过词面校验：查询切段后任一 ≥2 字段命中原文才放行"""
    session = _FakeSession([(11, "生鲜商品的保质期是多久？", "冷藏 3 天", 9.0)])
    faqs = [{"id": 11, "question": "生鲜商品的保质期是多久？", "answer": "冷藏 3 天", "keywords": "储存方法"}]
    r = _retriever(vector_hits=[], faqs=faqs, session=session)
    hits = r.retrieve("生鲜 保质期多久")
    assert [h["id"] for h in hits] == [11]
    assert hits[0]["fulltext_hit"] is True
    assert hits[0]["keyword_hit"] is False


def test_fulltext_lane_drops_domain_out_request():
    """「推荐一部电影」剥掉停用词只剩「一部电影」：BM25 即使召回也过不了词面校验"""
    session = _FakeSession([(11, "生鲜商品的保质期是多久？", "冷藏 3 天", 9.0)])
    faqs = [{"id": 11, "question": "生鲜商品的保质期是多久？", "answer": "冷藏 3 天", "keywords": "储存方法"}]
    r = _retriever(vector_hits=[], faqs=faqs, session=session)
    assert r.retrieve("推荐一部电影") == []


def test_fulltext_lane_failure_degrades_to_two_lanes():
    """全文索引缺失/库故障 → 通道退场，向量+关键词两路照常工作"""
    r = _retriever()  # _BrokenSession
    hits = r.retrieve("客服电话是多少")
    assert hits and hits[0]["id"] == 1


def test_fulltext_segments_short_query_skipped():
    """剥掉停用词后没有 ≥2 字片段（如「怎么样」）→ 不发起全文查询"""
    session = _FakeSession()
    r = _retriever(vector_hits=[], session=session)
    r.retrieve("怎么样")
    assert session.queries == []


# ---------- RRF 融合排序 ----------

def test_rrf_prefers_multi_lane_hits_and_lexical_tiebreak():
    """向量+关键词双通道命中的排前；RRF 并列时（单通道各一名）关键词命中优先"""
    session = _FakeSession()
    r = _retriever(session=session)
    hits = r.retrieve("怎么退款 客服")
    # id2: 向量 rank1 + 关键词 rank1 → 2/(60+1+)；id1: 仅关键词
    assert hits[0]["id"] == 2 and hits[1]["id"] == 1
    # 对外 score 即 RRF 分；证据标记分别正确
    assert hits[0]["keyword_hit"] is True
    assert hits[1]["keyword_hit"] is True


def test_references_friendly_fields_only():
    """返回条目不携带内部字段（_best 已剔除），结构稳定可序列化"""
    r = _retriever()
    hits = r.retrieve("怎么退款 客服")
    assert all(set(h.keys()) == {"id", "question", "answer", "score", "keyword_hit", "fulltext_hit"}
               for h in hits)


# ---------- 确认带：商品域条目临界语义需词面佐证（2026-10-24 复测的油价泄漏修复）----------

def test_band_product_entry_without_evidence_is_dropped():
    """「现在油价多少」→ 食用油类目条目 0.596：商品域条目只凭语义进带、无词面佐证 → 拒答"""
    hits = [
        {"id": 277, "question": "「食用油」类目都有哪些商品？", "score": 0.596,
         "category": "category_knowledge"},
    ]
    faqs = [{"id": 277, "question": "「食用油」类目都有哪些商品？", "answer": "在售5款", "keywords": "食用油"}]
    r = Retriever(FakeVectorStore(hits), FakeFaqRepo(faqs), top_k=5,
                  session_factory=lambda: _BrokenSession())
    assert r.retrieve("现在油价多少") == []


def test_band_platform_entry_without_evidence_survives():
    """平台规则条目在带内只凭语义即可信（实测 10/10 正确）：「总价是怎么算出来的」0.557 → 保留"""
    hits = [
        {"id": 19, "question": "运费怎么计算？", "score": 0.557, "category": "shipping"},
    ]
    faqs = [{"id": 19, "question": "运费怎么计算？", "answer": "满 99 包邮", "keywords": "运费,邮费"}]
    r = Retriever(FakeVectorStore(hits), FakeFaqRepo(faqs), top_k=5,
                  session_factory=lambda: _BrokenSession())
    hits_out = r.retrieve("总价是怎么算出来的")
    assert [h["id"] for h in hits_out] == [19]
    assert hits_out[0]["keyword_hit"] is False  # 无词面佐证仍放行：平台规则条目不加验


def test_band_product_entry_with_keyword_evidence_survives():
    """商品域条目落带但有关键词佐证 → 放行（模板查询含商品名即此形态）"""
    hits = [
        {"id": 113, "question": "「小榨菜籽油 5L」这个商品有什么特点？", "score": 0.528,
         "category": "product_knowledge"},
    ]
    faqs = [{"id": 113, "question": "「小榨菜籽油 5L」这个商品有什么特点？",
             "answer": "物理压榨 非转基因", "keywords": "菜籽油,食用油"}]
    r = Retriever(FakeVectorStore(hits), FakeFaqRepo(faqs), top_k=5,
                  session_factory=lambda: _BrokenSession())
    hits_out = r.retrieve("小榨菜籽油 5L怎么储存比较好")
    assert [h["id"] for h in hits_out] == [113]
    assert hits_out[0]["keyword_hit"] is True
