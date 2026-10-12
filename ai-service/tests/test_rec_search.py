"""搜索框语义召回接口测试：min-score 闸门 / 全滤空退回 / 偏好翻转 / breakdown / 鉴权"""
import pytest
from fastapi import HTTPException

import app.main as main_mod
from app.api import rec_feed
from app.config import settings


class FakeProduct:
    def __init__(self, pid, name, sales, harvest_date=None, shelf_life=None):
        self.id = pid
        self.name = name
        self.sales = sales
        self.harvest_date = harvest_date
        self.shelf_life = shelf_life


PRODUCTS = [FakeProduct(1, "雪梨", 100), FakeProduct(2, "脐橙", 90), FakeProduct(3, "大米", 80)]
BY_ID = {p.id: p for p in PRODUCTS}


class _NullSessionCtx:
    def __enter__(self):
        return None

    def __exit__(self, *args):
        return False


class FakeStore:
    def __init__(self, hits):
        self._hits = hits

    def search(self, question, top_k=None):
        return self._hits


@pytest.fixture
def stub(monkeypatch):
    state = {"pref": {}, "browse": {}, "hits": [
        {"id": 1, "score": 0.6},
        {"id": 2, "score": 0.6},
        {"id": 3, "score": 0.2},  # 低于 0.35 闸门，应被过滤
    ]}

    monkeypatch.setattr(
        main_mod, "get_product_store", lambda: FakeStore(state["hits"])
    )
    monkeypatch.setattr(
        rec_feed.product_repo,
        "list_by_ids",
        lambda s, ids: [BY_ID[i] for i in ids if i in BY_ID],
    )
    monkeypatch.setattr(rec_feed.product_repo, "category_by_ids", lambda s, ids: {1: 10, 2: 20, 3: 30})
    monkeypatch.setattr(rec_feed.review_repo, "positive_rates", lambda s: {1: 50.0, 2: 100.0})
    monkeypatch.setattr(
        rec_feed.preference_repo,
        "preference_with_browse",
        lambda s, uid: (state["pref"], state["browse"]),
    )
    monkeypatch.setattr(rec_feed, "SessionLocal", lambda: _NullSessionCtx())
    monkeypatch.setattr(settings, "JAVA_INTERNAL_KEY", "k")
    return state


def _call(query="梨", user_id=None, limit=50, key="k"):
    return rec_feed.rec_search(
        rec_feed.SearchRequest(query=query, userId=user_id, limit=limit), x_internal_key=key
    )


def test_min_score_gate_drops_weak_hits(stub):
    resp = _call()
    ids = [i["id"] for i in resp["items"]]
    assert 3 not in ids  # 0.2 < 0.35 被闸门剔除
    assert resp["candidates"] == 2


def test_all_below_threshold_returns_empty_for_fulltext_fallback(stub):
    stub["hits"] = [{"id": 1, "score": 0.3}, {"id": 2, "score": 0.1}]
    resp = _call()
    assert resp["items"] == []
    assert resp["candidates"] == 0


def test_preference_flips_tied_semantic(stub):
    # 语义同分（0.6/0.6），游客下高好评率的脐橙(2)在前
    assert _call()["items"][0]["id"] == 2
    # 登录且强偏好雪梨类目(10)：α·1.0 足以翻转为雪梨第一
    stub["pref"] = {10: 1.0}
    assert _call(user_id=7)["items"][0]["id"] == 1


def test_personalized_flag_and_breakdown(stub):
    stub["pref"] = {10: 1.0}
    resp = _call(user_id=7)
    assert resp["personalized"] is True
    for item in resp["items"]:
        assert item["score"] == pytest.approx(sum(item["breakdown"].values()), abs=1e-3)
        assert "sem" in item["breakdown"]


def test_invalid_key_rejected(stub):
    with pytest.raises(HTTPException):
        _call(key="wrong")
