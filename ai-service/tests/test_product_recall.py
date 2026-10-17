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
from app.config import settings
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
_DEFAULT_PRODUCT_ROW = {
    "id": 900, "name": "赣南脐橙 10斤", "subtitle": "皮薄多汁", "price": 19.9,
    "sales": 500, "origin": "江西赣州", "storage": "阴凉干燥处", "shelfLife": "7天",
    "mainImage": "https://img/900.jpg",
}


class ApiStub:
    def __init__(self, product_row=_DEFAULT_PRODUCT_ROW):
        self.calls = []
        # 商详页咨询上下文用：productId → 该商品的事实（None 表示已下架/不存在）
        self.product_row = product_row

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

    def fetch_product(self, product_id):
        """商详页进客服：Java 侧已过滤仅在售，下架/不存在返回 None"""
        self.calls.append({"fetch_product": product_id})
        return self.product_row

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


class WeakScorePStore:
    """相似度全在闸门之下：模拟"推荐部电影"这类域外问法（向量索引仍会给分）"""

    def __init__(self, floor=None):
        self.floor = floor if floor is not None else 0.2

    def search(self, question, top_k=None):
        return [{"id": 1, "score": self.floor}, {"id": 2, "score": round(self.floor - 0.03, 4)}]


def test_recommend_gates_off_domain_query_out_of_vector_lane():
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = _run_node(node, question="推荐一部电影")
    # 低于 REC_MIN_SCORE 的召回不作为语义命中：不进 id 通道，降级关键词/热销
    assert all(c["ids"] is None for c in api.calls)
    assert out["tool_type"] == "recommend"
    assert "五常大米" in out["tool_result"]  # 无判定→热销兜底，绝不硬推语义无关商品


def test_recommend_refuses_when_model_marks_out_of_domain():
    """语义无强命中 + LIKE 无命中 + inDomain=false → 确定性拒答，不再降级热销榜。

    量化实验五：0.35 闸门下 10 条域外推荐请求放行 6 条（"手机壳→带壳毛豆"）。
    对话侧闸门独立为 0.47，并补上这个拒答出口。
    """
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore(floor=0.44))
    out = node({
        "messages": [_Human("有没有好看的冬天外套")],
        "rec_keyword": None, "rec_max_price": None, "rec_in_domain": False,
    })
    assert out["tool_type"] == "recommend_refused"
    assert out["tool_result"] == ""
    assert api.calls == []  # 一个商品都没查，没有把无关农产品端上桌


def test_recommend_uses_chat_gate_not_search_gate():
    """0.44 落在搜索框闸门(0.35)之上、对话闸门(0.47)之下：对话侧必须判为未命中。

    两个闸门必须分开——搜索框空结果能退回全文检索，导购分支没有退路。
    """
    assert settings.REC_MIN_SCORE > settings.REC_SEARCH_MIN_SCORE
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore(floor=0.44))
    out = _run_node(node, question="有什么好米推荐")
    assert all(c["ids"] is None for c in api.calls)  # 未走语义 id 通道
    assert out["tool_type"] == "recommend"  # 无判定 → 热销兜底


def test_recommend_keeps_hits_at_or_above_gate():
    api = ApiStub()

    class EdgeStore:
        def search(self, question, top_k=None):
            floor = settings.REC_MIN_SCORE
            return [{"id": 2, "score": floor}, {"id": 1, "score": round(floor - 0.01, 4)}]

    node = make_recommend_node(api, llm=None, product_store=EdgeStore())
    out = _run_node(node)
    assert api.calls[0]["ids"] == [2]  # 阈值本身算命中（>=），临界下一档被滤掉
    assert "赣南脐橙" in out["tool_result"]


def test_recommend_prefers_like_hit_over_domain_label():
    """品类词 LIKE 命中 = 库里确实在卖 → 即使模型说 inDomain=false 也照常推荐"""
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = node({
        "messages": [_Human("推荐个附近的健身房")],
        "rec_keyword": "水果", "rec_max_price": None, "rec_in_domain": False,
    })
    assert out["tool_type"] == "recommend"
    assert "赣南脐橙" in out["tool_result"]


# ---------- 结构化商品卡（rec_items）与商详页咨询上下文（product_qa）----------
def _state(question="有什么好米推荐", **kw):
    base = {"messages": [_Human(question)], "rec_keyword": None,
            "rec_max_price": None, "rec_in_domain": None,
            "product_context": "", "product_item": None, "rec_items": []}
    base.update(kw)
    return base


def test_recommend_returns_structured_items_for_cards():
    """导购命中时除文本外必须给出卡片数据：图片/价格/id 进了 LLM 文本就丢了，
    用户看完推荐还得自己再搜一遍，导购链路在 UI 上等于断掉。"""
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=PStoreStub(ids=[2, 1]))
    out = node(_state())
    assert out["tool_type"] == "recommend"
    assert [p["id"] for p in out["rec_items"]] == [2, 1]  # 与召回序一致，供前端按序渲染
    assert '"五常大米"' in out["tool_result"]            # 文本仍是 LLM 组话术的依据


def test_recommend_refusal_carries_no_items():
    """域外拒答不能带任何商品卡——带了就等于把"跑步鞋→金华火腿"重新塞回去。"""
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore(floor=0.44))
    out = node(_state("推荐一双跑步鞋", rec_in_domain=False))
    assert out["tool_type"] == "recommend_refused"
    assert out["rec_items"] == []


def test_browsing_product_falls_to_product_qa_with_card():
    """商详页进客服问"这个能放多久"：既无语义命中又无品类词，
    默认指代就是他手上这件——按商品事实回答并给出该商品卡片，而不是热销榜或拒答。"""
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = node(_state("这个能放多久", productId=900))
    assert out["tool_type"] == "product_qa"
    assert [p["id"] for p in out["rec_items"]] == [900]
    assert "储存方式：阴凉干燥处" in out["tool_result"]
    assert "保质期：7天" in out["tool_result"]
    assert {"fetch_product": 900} in api.calls


def test_product_qa_beats_refusal_even_when_model_says_out_of_domain():
    """有明确指代对象时优先回答这件商品，不因 inDomain=false 就拒答。"""
    api = ApiStub()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = node(_state("这个适合送人吗", productId=900, rec_in_domain=False))
    assert out["tool_type"] == "product_qa"


def test_off_sale_product_falls_back_to_normal_lane():
    """商品已下架（Java 过滤后为 None）→ 当作没有上下文，走原有三级降级，绝不报错。"""
    api = ApiStub(product_row=None)
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = node(_state("这个能放多久", productId=900))
    assert out["tool_type"] == "recommend"        # 无判定 → 热销兜底
    assert out["product_item"] is None
    assert out["rec_items"]


def test_product_context_failure_is_silent():
    """Java 取上下文失败不能拖垮聊天链路：降级为无商品上下文继续问答。"""

    class BoomApi(ApiStub):
        def fetch_product(self, product_id):
            raise RuntimeError("java down")

    api = BoomApi()
    node = make_recommend_node(api, llm=None, product_store=WeakScorePStore())
    out = node(_state("这个能放多久", productId=900))
    assert out["product_context"] == ""
    assert out["tool_type"] == "recommend"        # 退回热销兜底，用户无感


# ---------- 生成节点：商品事实可以顶替"检索为空即拒答" ----------
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage


class _LLM:
    def __init__(self):
        self.seen = []

    def invoke(self, messages):
        self.seen = [m.content for m in messages if isinstance(m, SystemMessage)]
        return AIMessage(content="generated")


def test_faq_empty_but_browsing_product_answers_instead_of_refusing():
    """知识库里查不到"这个能放多久"，但答案就在当前商品的事实里——
    此时确定性拒答会把正确答案挡在门外，应改为基于商品事实由 LLM 回答。"""
    from app.agent.nodes import make_generate_node

    llm = _LLM()
    node = make_generate_node(llm)
    out = node({
        "messages": [HumanMessage(content="这个能放多久")],
        "intent": "faq", "tool_result": "", "tool_type": "",
        "faq_context": [],
        "product_context": "名称：赣南脐橙 10斤｜储存方式：阴凉干燥处｜保质期：7天",
    })
    assert out["messages"][-1].content == "generated"
    assert "保质期：7天" in llm.seen[0]
    assert "当前商品信息" in llm.seen[0]


def test_faq_empty_and_no_product_still_refuses_deterministically():
    """回归保护：没有商品上下文时，检索为空依旧是确定性拒答、不调 LLM。"""
    from app.agent.nodes import make_generate_node

    llm = _LLM()
    node = make_generate_node(llm)
    out = node({
        "messages": [HumanMessage(content="今天股市怎么样")],
        "intent": "faq", "tool_result": "", "tool_type": "",
        "faq_context": [], "product_context": "",
    })
    assert "知识库还未覆盖" in out["messages"][-1].content
    assert llm.seen == []          # 一次 LLM 都没花


def test_faq_hit_prompt_carries_product_context():
    """知识库命中且有商品上下文时，两者一起进 prompt，让"这个"有指代对象。"""
    from app.agent.nodes import make_generate_node

    llm = _LLM()
    node = make_generate_node(llm)
    node({
        "messages": [HumanMessage(content="这个包邮吗")],
        "intent": "faq", "tool_result": "", "tool_type": "",
        "faq_context": [{"id": 1, "question": "运费怎么算？", "answer": "满 39 包邮", "score": 0.8}],
        "product_context": "名称：赣南脐橙 10斤｜售价：19.9 元",
    })
    assert "满 39 包邮" in llm.seen[0]
    assert "用户当前正在浏览的商品" in llm.seen[0]
