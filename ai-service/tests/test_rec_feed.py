"""首页个性化 feed 接口测试：游客混合榜 / 偏好翻转排序 / breakdown 一致性 / 鉴权"""
import pytest
from fastapi import HTTPException

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


class _NullSessionCtx:
    def __enter__(self):
        return None

    def __exit__(self, *args):
        return False


@pytest.fixture
def stub_repos(monkeypatch):
    # rates：已收缩过的逐商品好评率；prior：全站好评先验，缺失评价的商品按它兜底
    state = {
        "pref": {},
        "browse": {},
        "rates": {1: 50.0, 2: 100.0},
        "prior": 66.67,
        "sales_max": 100,
    }

    monkeypatch.setattr(rec_feed.product_repo, "list_on_sale", lambda s: PRODUCTS)
    monkeypatch.setattr(rec_feed.product_repo, "category_by_ids", lambda s, ids: {1: 10, 2: 20, 3: 30})
    monkeypatch.setattr(rec_feed.product_repo, "max_sales", lambda s: state["sales_max"])
    monkeypatch.setattr(
        rec_feed.review_repo, "positive_rates", lambda s: (state["rates"], state["prior"])
    )
    monkeypatch.setattr(
        rec_feed.preference_repo,
        "preference_with_signals",
        lambda s, uid: (state["pref"], state["browse"]),
    )
    monkeypatch.setattr(rec_feed, "SessionLocal", lambda: _NullSessionCtx())
    monkeypatch.setattr(settings, "JAVA_INTERNAL_KEY", "k")
    return state


def _call(user_id=None, limit=10, key="k"):
    return rec_feed.rec_feed(rec_feed.FeedRequest(userId=user_id, limit=limit), x_internal_key=key)


def test_guest_mixed_rank_no_preference(stub_repos):
    resp = _call()
    assert resp["personalized"] is False
    ids = [i["id"] for i in resp["items"]]
    # 游客无偏好：销量差极小（100/90/80 归一后），好评率 100% 的脐橙应压过高销低评的雪梨
    assert ids[0] == 2
    assert len(ids) == 3


def test_preference_flips_order(stub_repos):
    stub_repos["pref"] = {10: 1.0}  # 雪梨类目强偏好
    ids = [i["id"] for i in _call(user_id=7)["items"]]
    assert ids[0] == 1  # α·1.0 足以翻转


def test_breakdown_sums_to_score(stub_repos):
    for item in _call()["items"]:
        assert item["score"] == pytest.approx(sum(item["breakdown"].values()), abs=1e-3)


def test_signals_are_raw_unweighted(stub_repos):
    items = {i["id"]: i for i in _call()["items"]}
    assert set(items[1]["signals"]) == {"sem", "pref", "sales", "rate", "fresh", "browse", "rate_conf"}
    # 首页 feed 无语义查询场景 → sem 恒 0；游客无偏好、FakeProduct 无 harvest → pref/fresh 恒 0
    for it in items.values():
        assert it["signals"]["sem"] == 0.0
        assert it["signals"]["pref"] == 0.0
        assert it["signals"]["fresh"] == 0.0
    # 原始 sales/rate 未乘权重：销量并列全库最高的雪梨(id1) sales 归一=1.0；好评率 id2=100%→1.0, id1=50%→0.5
    assert items[1]["signals"]["sales"] == pytest.approx(1.0)
    assert items[1]["signals"]["rate"] == pytest.approx(0.5)
    assert items[2]["signals"]["rate"] == pytest.approx(1.0)
    # breakdown 贡献 = 原始信号 × 权重（≤原始值）
    assert items[1]["breakdown"]["rate"] == pytest.approx(0.10 * items[1]["signals"]["rate"])


def test_zero_review_product_uses_global_prior(stub_repos):
    """无评价 ≠ 零好评：大米(id3) 没有评价记录，好评信号取全站先验而非 0，并标记为无评价证据"""
    items = {i["id"]: i for i in _call()["items"]}
    assert items[3]["signals"]["rate"] == pytest.approx(0.6667)
    # rate_conf 给理由端用：先验值是中性常数，不能让 Java 把它打成"好评率高"
    assert items[3]["signals"]["rate_conf"] == 0.0
    assert items[1]["signals"]["rate_conf"] == 1.0
    assert items[2]["signals"]["rate_conf"] == 1.0


def test_sales_norm_uses_whole_catalog_base_not_candidate_max(stub_repos):
    """销量归一基准取全库而非当前候选集：候选集里只有低销商品时，归一值应整体压低而非顶到 1.0"""
    stub_repos["sales_max"] = 1000
    items = {i["id"]: i for i in _call()["items"]}
    assert items[1]["signals"]["sales"] < 1.0
    # 同一条商品在全库基准下的分，不随候选集内相对销量排名变化而失真
    assert items[1]["signals"]["sales"] > items[3]["signals"]["sales"]


def test_limit_and_candidates(stub_repos):
    resp = _call(limit=2)
    assert len(resp["items"]) == 2
    assert resp["candidates"] == 3


def test_invalid_key_rejected(stub_repos):
    with pytest.raises(HTTPException):
        _call(key="wrong")


def test_freshness_breaks_tie(stub_repos, monkeypatch):
    from datetime import date, timedelta

    today = date.today()
    # 销量相同、无口碑差异，仅采摘日期不同：现摘短保菜新鲜度 > 隔月耐储米
    fresh_items = [
        FakeProduct(5, "陈米", 100, harvest_date=today - timedelta(days=50), shelf_life="18个月"),
        FakeProduct(6, "现摘青菜", 100, harvest_date=today, shelf_life="7天"),
    ]
    monkeypatch.setattr(rec_feed.product_repo, "list_on_sale", lambda s: fresh_items)
    items = _call()["items"]
    assert items[0]["id"] == 6
    assert "fresh" in items[0]["breakdown"]
    assert items[0]["breakdown"]["fresh"] > items[1]["breakdown"]["fresh"]
    for it in items:
        assert it["score"] == pytest.approx(sum(it["breakdown"].values()), abs=1e-3)


def test_missing_harvest_zero_freshness(stub_repos):
    # 默认 FakeProduct 无 harvest_date → 新鲜项恒为 0，不影响原有排序
    for item in _call()["items"]:
        assert item["breakdown"]["fresh"] == 0.0


def test_browse_signal_for_reason(stub_repos):
    """浏览强度单列在 signals.browse：只服务"最近看过"理由标签，不额外进 breakdown"""
    stub_repos["pref"] = {10: 1.0}
    stub_repos["browse"] = {10: 1.0, 30: 0.4}
    items = {i["id"]: i for i in _call(user_id=7)["items"]}
    assert items[1]["signals"]["browse"] == pytest.approx(1.0)
    assert items[3]["signals"]["browse"] == pytest.approx(0.4)
    assert items[2]["signals"]["browse"] == 0.0  # 没浏览过的类目不给假标签
    for it in items.values():
        assert it["score"] == pytest.approx(sum(it["breakdown"].values()), abs=1e-3)
