"""双路召回测试：向量召回 + 关键词命中合并去重 + 相似度阈值拒答"""
from app.rag.retriever import Retriever


class FakeVectorStore:
    """模拟向量召回结果（倒序更贴切：score 越大越相关）"""

    def __init__(self, hits=None):
        self._hits = [{"id": 2, "question": "怎么退款？", "score": 0.85}] if hits is None else hits

    def query(self, question, top_k=5):
        return self._hits


class FakeFaqRepo:
    def __init__(self):
        self.faqs = [
            {"id": 1, "question": "怎么联系人工客服？", "answer": "电话 400", "keywords": "客服,电话"},
            {"id": 2, "question": "怎么退款？", "answer": "申请售后", "keywords": "退款,售后"},
        ]

    def list_enabled(self, session=None):
        return [type("F", (), f)() for f in self.faqs]


def test_dual_recall_merge():
    r = Retriever(FakeVectorStore(), FakeFaqRepo(), top_k=5)
    hits = r.retrieve("怎么退款 客服")
    ids = [h["id"] for h in hits]
    # 关键词命中"客服"的 id=1 应进入结果，且与向量命中的 id=2 去重
    assert 1 in ids and 2 in ids
    assert len(hits) == 2
    assert all("answer" in h for h in hits)


def test_keyword_only_recall():
    r = Retriever(FakeVectorStore(), FakeFaqRepo(), top_k=5)
    hits = r.retrieve("客服电话是多少")
    assert hits[0]["id"] == 1


# ---------- 相似度阈值拒答（反幻觉闸门）----------
def test_threshold_filters_low_score_vector_hits():
    """域外/弱相关（向量得分低于阈值且无关键词命中）→ 整体拒答返回空"""
    r = Retriever(
        FakeVectorStore(hits=[{"id": 2, "question": "怎么退款？", "score": 0.31}]),
        FakeFaqRepo(),
        top_k=5,
        score_threshold=0.5,
    )
    assert r.retrieve("怎么看今天的股市行情") == []


def test_threshold_keeps_high_score_hits():
    r = Retriever(FakeVectorStore(), FakeFaqRepo(), top_k=5, score_threshold=0.5)
    hits = r.retrieve("怎么退款")
    assert [h["id"] for h in hits] == [2]


def test_keyword_hit_survives_below_threshold():
    """关键词精确命中不受阈值限制：向量零召回时关键词仍可入榜"""
    r = Retriever(FakeVectorStore(hits=[]), FakeFaqRepo(), top_k=5, score_threshold=0.9)
    hits = r.retrieve("客服电话是多少")
    assert hits and hits[0]["id"] == 1 and hits[0]["keyword_hit"] is True


def test_threshold_disabled_when_zero():
    """score_threshold=0 → 关闭阈值，低分向量召回仍返回（旧行为）"""
    r = Retriever(
        FakeVectorStore(hits=[{"id": 2, "question": "怎么退款？", "score": 0.31}]),
        FakeFaqRepo(),
        top_k=5,
        score_threshold=0,
    )
    hits = r.retrieve("随便什么问题")
    assert [h["id"] for h in hits] == [2]
