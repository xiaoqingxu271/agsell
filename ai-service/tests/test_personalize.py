"""个性化重排单测：偏好合并打分纯函数 + Personalizer 失败回退 + 节点接线

全部 stub（不连 MySQL、不调 LLM），验证：
- final_scores：语义分 + α·类目偏好 + β·销量归一 + γ·好评率 的相对序
- Personalizer.rerank：正常重排 / 游客不叠加偏好 / 任意异常回退召回原序
- recommend 节点：向量召回命中后经 personalizer 重排后再进生成上下文
"""
from app.agent.nodes import make_recommend_node
from app.config import settings
from app.rag.personalize import Personalizer, final_scores, sales_norm


class FakeSession:
    def __init__(self, holder):
        self.holder = holder

    def close(self):
        self.holder.setdefault("closed", 0)
        self.holder["closed"] += 1


def _factory(holder):
    def make():
        return FakeSession(holder)

    return make


# ---------- 纯函数 ----------
def test_sales_norm_relative_to_candidate_set():
    products = [{"id": 1, "sales": 999}, {"id": 2, "sales": 9}, {"id": 3, "sales": 0}]
    sn = sales_norm(products)
    assert sn[1] == 1.0
    assert sn[3] == 0.0
    assert sn[2] < sn[1]


def test_final_scores_pref_can_flip_close_semantic_order():
    products = [
        {"id": 1, "sales": 100, "positiveRate": None},
        {"id": 2, "sales": 100, "positiveRate": None},
    ]
    sem = {1: 0.60, 2: 0.58}  # 语义几乎并列
    cat_by_id = {1: 10, 2: 20}
    plain = final_scores(products, sem, {}, cat_by_id)
    assert plain[1] > plain[2]  # 无偏好：语义分 1 领先
    personal = final_scores(products, sem, {20: 1.0}, cat_by_id)
    assert personal[2] > personal[1]  # 用户偏好 2 的类目 → 反超，且增益恰为 α


def test_final_scores_uses_rating_and_falls_back_zero():
    import pytest

    products = [{"id": 1, "sales": 0, "positiveRate": 100}, {"id": 2, "sales": 0, "positiveRate": None}]
    s = final_scores(products, {1: 0.5, 2: 0.5}, {}, {1: 7, 2: 7})
    assert s[1] - s[2] == pytest.approx(settings.REC_RATE_GAMMA)  # 好评率 100 → +γ


# ---------- Personalizer ----------
def test_personalizer_guest_skips_preference(monkeypatch):
    from app.repositories import preference_repo, product_repo

    holder = {}
    monkeypatch.setattr(product_repo, "category_by_ids", lambda s, ids: {1: 10, 2: 20})
    called = []

    def fake_pref(session, user_id):
        called.append(user_id)
        return {20: 1.0}

    monkeypatch.setattr(preference_repo, "user_preference", fake_pref)

    p = Personalizer(_factory(holder))
    products = [{"id": 1, "sales": 5}, {"id": 2, "sales": 5}]
    out = p.rerank(None, products, {1: 0.6, 2: 0.58})
    assert called == []  # 游客不查偏好
    assert [x["id"] for x in out] == [1, 2]  # 语义序保持
    assert holder["closed"] == 1


def test_personalizer_user_pref_reranks(monkeypatch):
    from app.repositories import preference_repo, product_repo

    monkeypatch.setattr(product_repo, "category_by_ids", lambda s, ids: {1: 10, 2: 20})
    monkeypatch.setattr(preference_repo, "user_preference", lambda s, uid: {20: 1.0})
    p = Personalizer(_factory({}))
    products = [{"id": 1, "sales": 10}, {"id": 2, "sales": 10}]
    out = p.rerank(5, products, {1: 0.60, 2: 0.58})
    assert [x["id"] for x in out] == [2, 1]


def test_personalizer_falls_back_on_db_error(monkeypatch):
    from app.repositories import product_repo

    def boom(session, ids):
        raise RuntimeError("db down")

    monkeypatch.setattr(product_repo, "category_by_ids", boom)
    p = Personalizer(_factory({}))
    products = [{"id": 1}, {"id": 2}]
    assert p.rerank(5, products, {1: 0.6, 2: 0.5}) == products  # 原序返回，不抛错


# ---------- 节点接线 ----------
class ApiStub:
    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        pool = {1: {"id": 1, "name": "A", "sales": 10}, 2: {"id": 2, "name": "B", "sales": 10}}
        return [pool[i] for i in (ids or []) if i in pool]

    def format_payload(self, data):
        import json

        return json.dumps(data, ensure_ascii=False)


class PStoreStub:
    def search(self, question, top_k=None):
        return [{"id": 1, "score": 0.6}, {"id": 2, "score": 0.58}]


class RerankStub:
    def __init__(self, order):
        self.order = order
        self.seen_user = "unset"

    def rerank(self, user_id, products, sem_by_id):
        self.seen_user = user_id
        return [p for pid in self.order for p in products if p["id"] == pid]


class _Human:
    def __init__(self, content):
        self.content = content


def test_node_applies_personalized_order():
    rer = RerankStub(order=[2, 1])
    node = make_recommend_node(ApiStub(), llm=None, product_store=PStoreStub(), personalizer=rer)
    out = node({"messages": [_Human("买点米")], "userId": 9})
    assert rer.seen_user == 9
    import json

    assert [p["id"] for p in json.loads(out["tool_result"])] == [2, 1]


def test_node_without_personalizer_keeps_recall_order():
    node = make_recommend_node(ApiStub(), llm=None, product_store=PStoreStub())
    out = node({"messages": [_Human("买点米")], "userId": 9})
    import json

    assert [p["id"] for p in json.loads(out["tool_result"])] == [1, 2]
