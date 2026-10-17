"""混合排序唯一实现的单测：全库销量基准 / Bayesian 好评兜底 / 分项一致性 / Personalizer 回退

全部 stub（不连 MySQL、不调 LLM），验证：
- sales_norm：按全库最大销量归一，候选集变化不改变单品得分（翻页/换入口不漂移）
- score_products：语义 + α·偏好 + β·销量 + γ·好评率 + δ·新鲜度 的相对序与分项一致性
- 零评价商品按全站均值兜底，不再被当成"口碑最差"
- Personalizer.rerank：正常重排 / 游客不叠加偏好 / 任意异常回退召回原序
- recommend 节点：向量召回命中后经 personalizer 重排后再进生成上下文
"""
import math
from datetime import date, timedelta

import pytest

from app.agent.nodes import make_recommend_node
from app.config import settings
from app.rag.personalize import Personalizer, sales_norm, score_products

SALES_MAX = 1000  # 全库基准（stub）


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


# ---------- 销量基准 ----------
def test_sales_norm_uses_global_baseline_not_candidate_set():
    products = [{"id": 1, "sales": 999}, {"id": 2, "sales": 9}, {"id": 3, "sales": 0}]
    full = sales_norm(products, SALES_MAX)
    subset = sales_norm(products[:2], SALES_MAX)
    assert full[3] == 0.0
    assert full[1] == pytest.approx(math.log10(1000) / math.log10(1001), abs=1e-4)
    # 候选集少一半，同一条商品的销量分不变（旧实现按候选集 max 归一，值会跳）
    assert subset[1] == full[1]
    assert subset[2] == full[2]


def test_sales_norm_clamps_when_sales_exceed_baseline():
    sn = sales_norm([{"id": 1, "sales": 5000}], SALES_MAX)
    assert sn[1] == 1.0


def test_sales_norm_survives_missing_baseline():
    """全库最大销量取不到（0/None，如刚建库或 MySQL 返回 NULL）时不炸：基准兜底为 1，超基准部分 clamp 到 1.0"""
    assert sales_norm([{"id": 1, "sales": 5}], 0)[1] == 1.0
    assert sales_norm([{"id": 1, "sales": 5}], None)[1] == 1.0
    assert sales_norm([{"id": 1, "sales": 0}], 0)[1] == 0.0


# ---------- 唯一打分函数 ----------
def _rows():
    return [{"id": 1, "name": "A", "sales": 100}, {"id": 2, "name": "B", "sales": 100}]


def test_pref_can_flip_close_semantic_order():
    sem = {1: 0.60, 2: 0.58}
    plain = score_products(_rows(), sem_by_id=sem, cat_by_id={1: 10, 2: 20}, sales_max=SALES_MAX)
    assert [i["id"] for i in plain] == [1, 2]  # 无偏好：语义分 1 领先

    personal = score_products(
        _rows(), sem_by_id=sem, pref_by_cat={20: 1.0}, cat_by_id={1: 10, 2: 20}, sales_max=SALES_MAX
    )
    assert [i["id"] for i in personal] == [2, 1]  # 偏好 2 的类目 → 反超
    assert personal[0]["breakdown"]["pref"] == pytest.approx(settings.REC_PREF_ALPHA)


def test_missing_review_falls_back_to_global_prior_not_zero():
    """零评价商品拿全站均值：过去按 0 处理等于把"没人评价"当"口碑最差"，新/时令商品被系统性压死"""
    rows = [{"id": 1, "name": "有评价", "sales": 100}, {"id": 2, "name": "零评价", "sales": 100}]
    scored = {
        i["id"]: i
        for i in score_products(
            rows,
            rate_by_id={1: 60.0},  # 收缩后 60%，低于全站均值
            rate_prior=80.0,
            cat_by_id={1: 7, 2: 7},
            sales_max=SALES_MAX,
        )
    }
    assert scored[2]["signals"]["rate"] == pytest.approx(0.8)  # 无数据 → 用 prior
    assert scored[2]["score"] > scored[1]["score"]  # 反而高于"有评价但低于均值"的商品


def test_fresh_term_is_included_for_every_lane():
    """导购/搜索/feed 共用同一函数：新鲜度项不再只存在于 feed（旧导购实现缺这一项，同品不同序）"""
    today = date.today()
    rows = [{"id": 1, "name": "现摘", "sales": 100}, {"id": 2, "name": "陈货", "sales": 100}]
    scored = score_products(
        rows,
        fresh_by_id={
            1: 1.0,
            2: 0.0,
        },
        cat_by_id={1: 7, 2: 7},
        sales_max=SALES_MAX,
    )
    assert scored[0]["id"] == 1
    assert scored[0]["breakdown"]["fresh"] == pytest.approx(settings.REC_FRESH_DELTA)
    assert scored[0]["score"] - scored[1]["score"] == pytest.approx(settings.REC_FRESH_DELTA)


def test_breakdown_sums_to_score_and_signals_stay_raw():
    rows = [{"id": 1, "name": "A", "sales": 500}, {"id": 2, "name": "B", "sales": 50}]
    scored = score_products(
        rows,
        sem_by_id={1: 0.5},
        pref_by_cat={10: 1.0},
        cat_by_id={1: 10, 2: 10},
        rate_by_id={1: 90.0},
        rate_prior=70.0,
        browse_by_cat={10: 0.4},
        sales_max=SALES_MAX,
    )
    for item in scored:
        assert item["score"] == pytest.approx(sum(item["breakdown"].values()), abs=1e-3)
        assert set(item["signals"]) == {"sem", "pref", "sales", "rate", "fresh", "browse", "rate_conf"}
        # signals 是未乘权重的原始 0~1，breakdown 是加权贡献
        assert item["breakdown"]["pref"] == pytest.approx(
            settings.REC_PREF_ALPHA * item["signals"]["pref"]
        )
        assert item["signals"]["browse"] == pytest.approx(0.4)
        assert "browse" not in item["breakdown"]  # 浏览已并入 pref，不重复计分
    conf = {item["id"]: item["signals"]["rate_conf"] for item in scored}
    assert conf == {1: 1.0, 2: 0.0}  # 只有 id1 有真实评价；id2 的 0.7 是先验，理由端不得称"好评率高"


def test_feed_and_recall_lane_share_one_implementation():
    """同候选、同旁路信号，feed（无语义项）与召回（语义同分）两条路口径完全一致"""
    rows = [{"id": 1, "name": "雪梨", "sales": 300}, {"id": 2, "name": "脐橙", "sales": 200}]
    common = dict(
        cat_by_id={1: 10, 2: 20},
        rate_by_id={2: 100.0},
        rate_prior=66.67,
        fresh_by_id={1: 0.9, 2: 0.1},
        sales_max=SALES_MAX,
    )
    feed = score_products(rows, **common)
    lane = score_products(rows, sem_by_id={1: 0.42, 2: 0.42}, **common)
    assert [i["id"] for i in feed] == [i["id"] for i in lane]
    for f, l in zip(feed, lane):
        assert l["score"] - f["score"] == pytest.approx(0.42, abs=1e-4)  # 只多语义项，其余分毫不差


# ---------- Personalizer ----------
def _stub_repos(monkeypatch, rates=None, harvest=None, sales_max=SALES_MAX, cats=None):
    from app.repositories import preference_repo, product_repo, review_repo

    monkeypatch.setattr(product_repo, "category_by_ids", lambda s, ids: cats or {})
    monkeypatch.setattr(product_repo, "harvest_by_ids", lambda s, ids: harvest or {})
    monkeypatch.setattr(product_repo, "max_sales", lambda s: sales_max)
    monkeypatch.setattr(review_repo, "positive_rates", lambda s: rates or ({}, 0.0))
    return preference_repo, product_repo, review_repo


def test_personalizer_guest_skips_preference(monkeypatch):
    _stub_repos(monkeypatch, cats={1: 10, 2: 20})
    from app.repositories import preference_repo

    called = []
    monkeypatch.setattr(preference_repo, "user_preference", lambda s, uid: called.append(uid) or {})

    p = Personalizer(_factory({}))
    products = [{"id": 1, "sales": 5}, {"id": 2, "sales": 5}]
    out = p.rerank(None, products, {1: 0.6, 2: 0.58})
    assert called == []  # 游客不查偏好
    assert [x["id"] for x in out] == [1, 2]  # 语义序保持


def test_personalizer_user_pref_reranks(monkeypatch):
    from app.repositories import preference_repo

    _stub_repos(monkeypatch, cats={1: 10, 2: 20})
    monkeypatch.setattr(preference_repo, "user_preference", lambda s, uid: {20: 1.0})
    p = Personalizer(_factory({}))
    products = [{"id": 1, "sales": 10}, {"id": 2, "sales": 10}]
    out = p.rerank(5, products, {1: 0.60, 2: 0.58})
    assert [x["id"] for x in out] == [2, 1]


def test_personalizer_freshness_can_rerank_recalled_products(monkeypatch):
    """导购链路现在也吃新鲜度：语义同分、销量同分时，现摘的排前面（旧实现完全不看采摘日期）"""
    _stub_repos(
        monkeypatch,
        cats={1: 10, 2: 20},
        harvest={
            1: (date.today() - timedelta(days=40), "18个月"),  # 耐储陈货
            2: (date.today(), "7天"),  # 现摘短保
        },
    )
    p = Personalizer(_factory({}))
    products = [{"id": 1, "sales": 10}, {"id": 2, "sales": 10}]
    out = p.rerank(None, products, {1: 0.60, 2: 0.60})
    assert [x["id"] for x in out] == [2, 1]


def test_personalizer_preserves_product_fields(monkeypatch):
    _stub_repos(monkeypatch, cats={1: 10})
    p = Personalizer(_factory({}))
    products = [{"id": 1, "name": "A", "sales": 1, "price": 9.9}]
    out = p.rerank(None, products, {1: 0.5})
    assert out == products  # 交给生成节点的仍是 Java 原始商品字典，不掺打分字段


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
