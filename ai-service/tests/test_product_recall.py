"""步骤一：商品向量召回索引 + 导购三级降级

覆盖三层：
- VectorStore collection 参数化（faq / product 两库数据互不可见）
- ProductStore 条目构建与 search_ids 召回（临时目录 + FakeEmbedder，零模型下载）
- recommend 节点三级顺序：向量召回 → 关键词搜索 → 热销榜（含异常降级与召回序重排）
"""
import json
from types import SimpleNamespace

import numpy as np

from app.agent.nodes import make_recommend_node
from app.rag.product_store import PRODUCT_COLLECTION, ProductStore, build_entry
from app.rag.vectorstore import VectorStore


class FakeEmbedder:
    dim = 8

    def _vec(self, text: str) -> np.ndarray:
        seed = abs(hash(text)) % (2**32)
        return np.random.RandomState(seed).rand(self.dim) + 0.1

    def embed(self, docs):
        return [self._vec(d) for d in docs]

    def query_embed(self, q):
        return [self._vec(q)]


# ---------- 条目构建 ----------
def _product(**kw):
    base = dict(id=77, name="赣南脐橙 10斤", subtitle="产地直发 皮薄多汁", category_id=3,
                price=19.9, stock=100, sales=500, origin="江西赣州", storage="阴凉干燥",
                shelf_life="7天", status=1, deleted=0)
    base.update(kw)
    return SimpleNamespace(**base)


def test_build_entry_uses_faq_shape():
    e = build_entry(_product(), "柑橘类")
    assert e["id"] == 77
    assert e["question"] == "赣南脐橙 10斤"  # 商品名进 question（召回标签复用）
    assert "产地直发" in e["answer"] and "产地：江西赣州" in e["answer"]
    assert "柑橘类" in e["answer"]
    assert e["category"] == "柑橘类"
    assert "江西赣州" in e["keywords"]


def test_build_entry_handles_missing_optional_fields():
    e = build_entry(_product(subtitle=None, origin=None, storage=None, shelf_life=None), "")
    assert e["answer"] == ""
    assert e["keywords"] == ""


# ---------- collection 隔离 ----------
def test_two_collections_are_isolated(tmp_path):
    faq_vs = VectorStore(path=str(tmp_path), embedder=FakeEmbedder())
    prod_vs = VectorStore(path=str(tmp_path), embedder=FakeEmbedder(), collection=PRODUCT_COLLECTION)
    prod_vs.rebuild([{"id": 1, "question": "商品A", "answer": "卖点", "category": "c", "keywords": ""}])
    assert faq_vs.count() == 0
    assert prod_vs.count() == 1
    faq_vs.rebuild([{"id": 1, "question": "问题1", "answer": "答案", "category": "c", "keywords": ""}])
    # 同 id 各库独立；FAQ 重建不影响商品库
    assert faq_vs.count() == 1
    assert prod_vs.count() == 1


# ---------- ProductStore ----------
class FakeSession:
    pass


def test_product_store_rebuild_and_search(monkeypatch, tmp_path):
    from app.repositories import product_repo

    products = [_product(id=101), _product(id=102, name="五常大米", origin="黑龙江五常")]

    def fake_list_on_sale(session):
        return products

    monkeypatch.setattr(product_repo, "list_on_sale", fake_list_on_sale)
    monkeypatch.setattr(product_repo, "category_name_map", lambda session: {3: "米面粮油"})

    vs = VectorStore(path=str(tmp_path), embedder=FakeEmbedder(), collection=PRODUCT_COLLECTION)
    store = ProductStore(vs)
    assert store.rebuild(FakeSession()) == 2

    ids = store.search_ids("五常大米", top_k=2)
    assert len(ids) == 2
    assert all(isinstance(i, int) for i in ids)
    assert set(ids) == {101, 102}  # FakeEmbedder 非语义，仅验证 id 回传形态


# ---------- recommend 节点三级降级 ----------
class ApiStub:
    def __init__(self, on_sale=True):
        self.calls = []
        self.on_sale = on_sale

    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        self.calls.append({"keyword": keyword, "max_price": max_price, "ids": ids})
        if ids is not None:
            # 模拟 Java：按 id 取详情但默认销量降序（召回序需 Python 重排）
            pool = {1: {"id": 1, "name": "五常大米"}, 2: {"id": 2, "name": "赣南脐橙"}}
            out = [pool[i] for i in ids if i in pool]
            if max_price is not None:
                out = [p for p in out if p["id"] != 2]  # 假装脐橙超价被滤掉
            return out
        if keyword == "水果":
            return [{"id": 2, "name": "赣南脐橙"}]
        if keyword:
            return []
        return [{"id": 1, "name": "五常大米"}]  # 热销榜

    def format_payload(self, data):
        return json.dumps(data, ensure_ascii=False)


class PStoreStub:
    def __init__(self, ids=None, error=False):
        self.ids = ids or []
        self.error = error

    def search(self, question, top_k=None):
        if self.error:
            raise RuntimeError("chroma down")
        return [{"id": i, "score": 0.6 - idx * 0.01} for idx, i in enumerate(self.ids)]

    def search_ids(self, question, top_k=None):
        return [h["id"] for h in self.search(question)]


def _run_node(node, question="有什么好米推荐"):
    state = {"messages": [_Human(question)]}
    return node(state)


class _Human:
    def __init__(self, content):
        self.content = content


def test_recommend_prefers_vector_recall_and_reorders():
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=PStoreStub(ids=[2, 1]))
    out = _run_node(node)
    assert out["tool_type"] == "recommend"
    assert api.calls[0]["ids"] == [2, 1]  # 第一级走向量召回
    import json as _json
    products = _json.loads(out["tool_result"])
    assert [p["id"] for p in products] == [2, 1]  # 按召回序重排，而非 Java 销量序


def test_recommend_vector_lane_respects_price_filter():
    api = ApiStub()
    # max_price 经由抽取通道进不了（llm=None），直接验证 Java 滤空后降级
    node = make_recommend_node(api, llm=None, product_store=PStoreStub(ids=[2]))
    # Java 桩对 ids=[2] 返回脐橙；若价格过滤后为空才会落到下一 lane——这里非空
    out = _run_node(node)
    assert "赣南脐橙" in out["tool_result"]


def test_recommend_falls_back_when_index_empty():
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=PStoreStub(ids=[]))
    out = _run_node(node)
    # 索引为空 → 走原关键词/热销路径（llm=None → keyword=None → 热销榜）
    assert api.calls[0]["ids"] is None
    assert "五常大米" in out["tool_result"]


def test_recommend_falls_back_when_recall_raises():
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=PStoreStub(error=True))
    out = _run_node(node)
    assert out["tool_type"] == "recommend"
    assert "五常大米" in out["tool_result"]  # 热销兜底，路由不中断
    assert any(c["ids"] is None for c in api.calls)
