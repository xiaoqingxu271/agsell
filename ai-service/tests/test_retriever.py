"""双路召回测试：向量召回 + 关键词命中合并去重"""
from app.rag.retriever import Retriever


class FakeVectorStore:
    """模拟向量召回结果（倒序更贴切：score 越大越相关）"""

    def query(self, question, top_k=5):
        return [{"id": 2, "question": "怎么退款？", "score": 0.85}]


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
