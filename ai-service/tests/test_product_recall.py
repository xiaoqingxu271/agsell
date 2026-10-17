"""商品向量召回 + product_search 执行器的五级闸门 + 生成节点的资料出口

覆盖三层：
- VectorStore collection 参数化（faq / product 两库数据互不可见）
- ProductStore 条目构建与 search_ids 召回（临时目录 + FakeEmbedder，零模型下载）
- product_search 执行器五级顺序：向量召回（REC_MIN_SCORE 闸门）→ 品类 LIKE →
  商详 product_qa → 域外 refused → 热销榜（含异常降级与召回序重排）
- 生成节点：只有 status=ok 的观察才算资料；故障/空结果/域外各走各的确定性出口

⚠ 这些闸门是量化实验实测出来的（实验五：0.35 闸门下 10 条域外请求放行 6 条），
所以断言写在执行器上——智能体只决定"调不调 product_search"，把关一律在这里。
"""
import json
from types import SimpleNamespace

import numpy as np
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.agent.executors import ToolContext, run_tool
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


# ---------- product_search 执行器：五级降级 ----------
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


class _Human:
    def __init__(self, content):
        self.content = content


def _rec(question="有什么好米推荐", *, api=None, product_store=None, personalizer=None,
         keyword=None, max_price=None, in_domain=None, **state_kw) -> dict:
    """跑一次 product_search 执行器，返回 Observation（参数缺省即"没有判定"，与不传一致）"""
    args: dict = {"query": question}
    if keyword is not None:
        args["keyword"] = keyword
    if max_price is not None:
        args["maxPrice"] = max_price
    if in_domain is not None:
        args["inDomain"] = in_domain
    ctx = ToolContext(product_store=product_store, personalizer=personalizer,
                      java_api_module=api or ApiStub())
    state = {"messages": [_Human(question)], "userId": None,
             "product_context": "", "product_item": None}
    state.update(state_kw)
    return run_tool(ctx, state, "product_search", args)


def test_recommend_prefers_vector_recall_and_reorders():
    api = ApiStub()
    obs = _rec(api=api, product_store=PStoreStub(ids=[2, 1]))
    assert (obs.kind, obs.status) == ("recommend", "ok")
    assert api.calls[0]["ids"] == [2, 1]  # 第一级走向量召回
    products = json.loads(obs.payload)
    assert [p["id"] for p in products] == [2, 1]  # 按召回序重排，而非 Java 销量序


def test_recommend_vector_lane_respects_price_filter():
    api = ApiStub()
    # Java 桩对 ids=[2] 返回脐橙；若价格过滤后为空才会落到下一 lane——这里非空
    obs = _rec(api=api, product_store=PStoreStub(ids=[2]))
    assert "赣南脐橙" in obs.payload


def test_recommend_falls_back_when_index_empty():
    api = ApiStub()
    obs = _rec(api=api, product_store=PStoreStub(ids=[]))
    # 索引为空 → 走原关键词/热销路径（无 keyword → 热销榜）
    assert api.calls[0]["ids"] is None
    assert "五常大米" in obs.payload


def test_recommend_falls_back_when_recall_raises():
    api = ApiStub()
    obs = _rec(api=api, product_store=PStoreStub(error=True))
    assert (obs.kind, obs.status) == ("recommend", "ok")
    assert "五常大米" in obs.payload  # 热销兜底，链路不中断
    assert any(c["ids"] is None for c in api.calls)


class WeakScorePStore:
    """相似度全在闸门之下：模拟"推荐部电影"这类域外问法（向量索引仍会给分）"""

    def __init__(self, floor=None):
        self.floor = floor if floor is not None else 0.2

    def search(self, question, top_k=None):
        return [{"id": 1, "score": self.floor}, {"id": 2, "score": round(self.floor - 0.03, 4)}]


def test_recommend_gates_off_domain_query_out_of_vector_lane():
    api = ApiStub()
    obs = _rec("推荐一部电影", api=api, product_store=WeakScorePStore())
    # 低于 REC_MIN_SCORE 的召回不作为语义命中：不进 id 通道，降级关键词/热销
    assert all(c["ids"] is None for c in api.calls)
    assert (obs.kind, obs.status) == ("recommend", "ok")
    assert "五常大米" in obs.payload  # 无判定→热销兜底，绝不硬推语义无关商品


def test_recommend_refuses_when_model_marks_out_of_domain():
    """语义无强命中 + LIKE 无命中 + inDomain=false → 确定性拒答，不再降级热销榜。

    量化实验五：0.35 闸门下 10 条域外推荐请求放行 6 条（"手机壳→带壳毛豆"）。
    对话侧闸门独立为 0.47，并补上这个拒答出口。
    """
    api = ApiStub()
    obs = _rec("有没有好看的冬天外套", api=api,
               product_store=WeakScorePStore(floor=0.44), in_domain=False)
    assert obs.status == "refused"
    assert obs.payload == ""
    assert api.calls == []  # 一个商品都没查，没有把无关农产品端上桌


def test_recommend_uses_chat_gate_not_search_gate():
    """0.44 落在搜索框闸门(0.35)之上、对话闸门(0.47)之下：对话侧必须判为未命中。

    两个闸门必须分开——搜索框空结果能退回全文检索，导购分支没有退路。
    """
    assert settings.REC_MIN_SCORE > settings.REC_SEARCH_MIN_SCORE
    api = ApiStub()
    obs = _rec(api=api, product_store=WeakScorePStore(floor=0.44))
    assert all(c["ids"] is None for c in api.calls)  # 未走语义 id 通道
    assert obs.status == "ok"                         # 无判定 → 热销兜底


def test_recommend_keeps_hits_at_or_above_gate():
    api = ApiStub()

    class EdgeStore:
        def search(self, question, top_k=None):
            floor = settings.REC_MIN_SCORE
            return [{"id": 2, "score": floor}, {"id": 1, "score": round(floor - 0.01, 4)}]

    obs = _rec(api=api, product_store=EdgeStore())
    assert api.calls[0]["ids"] == [2]  # 阈值本身算命中（>=），临界下一档被滤掉
    assert "赣南脐橙" in obs.payload


def test_recommend_prefers_like_hit_over_domain_label():
    """品类词 LIKE 命中 = 库里确实在卖 → 即使模型说 inDomain=false 也照常推荐"""
    obs = _rec("推荐个附近的健身房", keyword="水果", in_domain=False,
               product_store=WeakScorePStore())
    assert obs.status == "ok"
    assert "赣南脐橙" in obs.payload


# ---------- 结构化商品卡（items）与商详页咨询上下文（product_qa）----------
def test_recommend_returns_structured_items_for_cards():
    """导购命中时除文本外必须给出卡片数据：图片/价格/id 进了 LLM 文本就丢了，
    用户看完推荐还得自己再搜一遍，导购链路在 UI 上等于断掉。"""
    obs = _rec(product_store=PStoreStub(ids=[2, 1]))
    assert obs.kind == "recommend"
    assert [p["id"] for p in obs.items] == [2, 1]  # 与召回序一致，供前端按序渲染
    assert '"五常大米"' in obs.payload             # 文本仍是 LLM 组话术的依据


def test_recommend_refusal_carries_no_items():
    """域外拒答不能带任何商品卡——带了就等于把"跑步鞋→金华火腿"重新塞回去。"""
    obs = _rec("推荐一双跑步鞋", product_store=WeakScorePStore(floor=0.44), in_domain=False)
    assert obs.status == "refused"
    assert obs.items == []


def test_browsing_product_falls_to_product_qa_with_card():
    """商详页进客服问"这个能放多久"：既无语义命中又无品类词，
    默认指代就是他手上这件——按商品事实回答并给出该商品卡片，而不是热销榜或拒答。"""
    api = ApiStub()
    obs = _rec("这个能放多久", api=api, productId=900, product_store=WeakScorePStore())
    assert obs.kind == "product_qa"
    assert [p["id"] for p in obs.items] == [900]
    assert "储存方式：阴凉干燥处" in obs.payload
    assert "保质期：7天" in obs.payload
    assert {"fetch_product": 900} in api.calls


def test_browsing_product_beats_strong_semantic_recall():
    """E2E 实测的坑：商详页问"这个能放多久"，语义召回照样给 8 条过闸门的结果（top 0.518，
    首条是金针菇）——指代句与真品类问句的向量分数重叠，调阈值救不了。
    判据换成结构性的：有 productId 且模型没点出品类词 → 问的就是眼前这件，卡片只给它的。"""

    class StrongStore:
        """模拟真实索引对指代句也给高分：分数与「哪种茶叶比较好」这类真问句不可分"""

        def search(self, question, top_k=None):
            return [{"id": 1, "score": 0.52}, {"id": 2, "score": 0.50}]

    api = ApiStub()
    obs = _rec("这个能放多久？怎么保存", api=api, productId=900, product_store=StrongStore())
    assert obs.kind == "product_qa"
    assert [p["id"] for p in obs.items] == [900]     # 卡片是他正在看的这件，不是金针菇
    assert api.calls == [{"fetch_product": 900}]      # 没有再去按 id 捞无关商品


def test_named_category_while_browsing_is_not_hijacked_by_product_qa():
    """反向保护：浏览某件商品时点名问别的品类（模型给了 keyword），不能被指代分支抢走。"""
    api = ApiStub()
    obs = _rec("有没有比这个更便宜的橙子", api=api, productId=900, keyword="水果",
               product_store=WeakScorePStore())
    assert obs.kind == "recommend"
    assert {"keyword": "水果", "max_price": None, "ids": None} in api.calls  # 真的拿去 LIKE
    assert [p["id"] for p in obs.items] == [2]         # 卡片是命中的橙子，不是 900


def test_like_hits_lead_and_semantic_fills_the_card_list():
    """品类词 LIKE 命中排最前（名字里真有这个词是最硬证据），但不再独占出口：
    实测"有什么水果推荐"LIKE 只剩「水果玉米 / 水果胡萝卜」两条，只给这两张卡"准而薄"，
    合并语义召回补齐后才既准又有得挑。两条通道去重后一起交给个性化与截断。"""

    class OrangeApi(ApiStub):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
            self.calls.append({"keyword": keyword, "max_price": max_price, "ids": ids})
            if keyword == "橙":
                return [{"id": 1, "name": "赣南脐橙"}, {"id": 2, "name": "秭归夏橙"}]
            if ids:
                return [{"id": i, "name": f"其他水果{i}"} for i in ids]
            return []

    api = OrangeApi()
    obs = _rec("有没有比这个便宜的橙子", api=api, keyword="橙",
               product_store=PStoreStub(ids=[3, 4]))
    assert [p["id"] for p in obs.items] == [1, 2, 3, 4]   # LIKE 在前，语义补齐在后
    assert api.calls[0] == {"keyword": "橙", "max_price": None, "ids": None}
    assert api.calls[1]["ids"] == [3, 4]                  # 语义那条通道也真的取了详情


def test_like_hits_survive_personalized_rerank():
    """个性化重排不能把用户点名品类的 LIKE 命中挤出手卡片列表。

    E2E 实测：登录用户问"有什么水果推荐"，LIKE 命中的水果玉米/水果胡萝卜被
    personalizer 按偏好/销量整批重排后挤出了前 4 张——重排是加分项，不该覆盖"点名要什么"。
    """
    class Reverse:
        """最恶劣的重排器：完全反转输入顺序（模拟个性化分把无关候选排到最前）"""

        def rerank(self, user_id, products, sem_by_id):
            return list(reversed(products))

    class OrangeApi(ApiStub):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
            self.calls.append({"keyword": keyword, "max_price": max_price, "ids": ids})
            if keyword == "橙":
                return [{"id": 1, "name": "赣南脐橙"}, {"id": 2, "name": "秭归夏橙"}]
            if ids:
                return [{"id": i, "name": f"其他水果{i}"} for i in ids]
            return []

    api = OrangeApi()
    obs = _rec("有没有比这个便宜的橙子", api=api, keyword="橙", userId=9,
               product_store=PStoreStub(ids=[3, 4]), personalizer=Reverse())
    ids = [p["id"] for p in obs.items]
    assert ids[:2] == [2, 1]          # LIKE 组内部被重排了，但整组仍在前
    assert ids[2:] == [4, 3]          # 补齐的语义候选排在后面


def test_card_list_is_capped():
    """卡片条数封顶：气泡下面一次冒出七八张卡，用户不会往下翻。"""
    class ManyApi(ApiStub):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
            self.calls.append({"keyword": keyword, "max_price": max_price, "ids": ids})
            return [{"id": i, "name": f"商品{i}"} for i in (ids or range(1, 10))]

    api = ManyApi()
    obs = _rec("有什么水果推荐", api=api, product_store=PStoreStub(ids=[1, 2, 3, 4, 5, 6, 7, 8]))
    assert len(obs.items) == settings.REC_CARD_LIMIT
    assert len(obs.items) < 8
    # payload 与卡片必须同源：LLM 讲的和用户点的不能是两份列表
    assert [p["id"] for p in json.loads(obs.payload)] == [p["id"] for p in obs.items]


def test_product_qa_beats_refusal_even_when_model_says_out_of_domain():
    """有明确指代对象时优先回答这件商品，不因 inDomain=false 就拒答。"""
    obs = _rec("这个适合送人吗", productId=900, in_domain=False, product_store=WeakScorePStore())
    assert obs.kind == "product_qa"


def test_off_sale_product_falls_back_to_normal_lane():
    """商品已下架（Java 过滤后为 None）→ 当作没有上下文，走原有五级降级，绝不报错。"""
    api = ApiStub(product_row=None)
    obs = _rec("这个能放多久", api=api, productId=900, product_store=WeakScorePStore())
    assert obs.kind == "recommend"        # 无判定 → 热销兜底
    assert [p["id"] for p in obs.items] == [1]  # 卡片是热销候选，不是那件已下架商品


def test_product_context_failure_is_silent():
    """Java 取上下文失败不能拖垮聊天链路：降级为无商品上下文继续问答。"""

    class BoomApi(ApiStub):
        def fetch_product(self, product_id):
            raise RuntimeError("java down")

    api = BoomApi()
    obs = _rec("这个能放多久", api=api, productId=900, product_store=WeakScorePStore())
    assert obs.kind == "recommend"        # 退回热销兜底，用户无感
    assert obs.status == "ok"


# ---------- 生成节点：资料出口分层 ----------
class _LLM:
    def __init__(self):
        self.seen = []

    def invoke(self, messages):
        self.seen = [m.content for m in messages if isinstance(m, SystemMessage)]
        return AIMessage(content="generated")


def _kb_obs(status="empty", payload=""):
    return {"tool": "kb_search", "kind": "kb", "status": status, "payload": payload, "items": []}


def _gen(product_context="", obs=(), intent="faq"):
    from app.agent.nodes import make_generate_node

    llm = _LLM()
    out = make_generate_node(llm)({
        "messages": [HumanMessage(content="这个能放多久")],
        "intent": intent, "observations": list(obs), "rec_items": [],
        "product_context": product_context, "product_item": None,
    })
    return llm, out


def test_kb_empty_but_browsing_product_answers_from_facts():
    """知识库里查不到"这个能放多久"，但答案就在当前商品的事实里——
    此时确定性拒答会把正确答案挡在门外，应改为基于商品事实由 LLM 回答。"""
    facts = "名称：赣南脐橙 10斤｜储存方式：阴凉干燥处｜保质期：7天"
    llm, out = _gen(product_context=facts, obs=[_kb_obs()])
    assert out["messages"][-1].content == "generated"
    assert "保质期：7天" in llm.seen[0]
    assert "当前商品信息" in llm.seen[0]


def test_kb_empty_and_no_product_still_refuses_deterministically():
    """回归保护：没有商品上下文时，检索为空依旧是确定性拒答、不调 LLM。"""
    llm, out = _gen(obs=[_kb_obs()])
    assert "知识库还未覆盖" in out["messages"][-1].content
    assert llm.seen == []  # 一次 LLM 都没花


def test_kb_hit_prompt_carries_product_context():
    """知识库命中且有商品上下文时，两者一起进 prompt，让"这个"有指代对象。"""
    llm, out = _gen(
        product_context="名称：赣南脐橙 10斤｜售价：19.9 元",
        obs=[_kb_obs(status="ok", payload="Q：运费怎么算？\nA：满 39 包邮")],
    )
    assert "满 39 包邮" in llm.seen[0]
    assert "用户当前正在浏览的商品" in llm.seen[0]


def test_no_tool_called_does_not_refuse_but_guides():
    """规划器一个工具都没调（本轮不需要事实）：不能回"知识库未覆盖"——根本没查过，
    拒得不成立。改为引导式回复，提示词本身禁止编造业务信息。"""
    llm, out = _gen(obs=[])
    assert "引导" in llm.seen[0] or "闲聊" in llm.seen[0]
    assert "知识库还未覆盖" not in out["messages"][-1].content


def test_retriever_failure_degrades_without_leaking_internals():
    """检索器故障（status=error，payload 已被执行器清空）→ 友好降级话术，
    既不说"知识库未覆盖"（那是资料结论），也绝不出现内部异常字样。"""
    boom = {"tool": "kb_search", "kind": "kb", "status": "error", "payload": "", "items": []}
    llm, out = _gen(obs=[boom])
    reply = out["messages"][-1].content
    assert "400-000-0000" in reply and "繁忙" in reply
    assert llm.seen == []  # 不经 LLM，省一次额度也不会编造


def test_multi_intent_observations_answered_in_one_pass():
    """一句话两个诉求：kb_search + product_search 两条观察一起进上下文，一次答完。"""
    llm, out = _gen(obs=[
        _kb_obs(status="ok", payload="Q：怎么申请退款？\nA：订单详情页提交售后申请"),
        {"tool": "product_search", "kind": "recommend", "status": "ok",
         "payload": '[{"id": 2, "name": "赣南脐橙"}]', "items": [{"id": 2, "name": "赣南脐橙"}]},
    ])
    ctx = llm.seen[0]
    assert "订单详情页提交售后申请" in ctx and "赣南脐橙" in ctx
    assert "逐个回应用户的每个诉求" in ctx
