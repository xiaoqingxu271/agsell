"""偏好聚合的信号测试（假 Session，不打真库）

第二批整改：偏好信号从三路扩到六路（订单/收藏/好评/加购/浏览/搜索词）。
覆盖：各通道 SQL 参数窗口、浏览衰减归一、六路合并权重、merged 与纯浏览分别返回。
"""
from datetime import date, timedelta

import pytest

from app.config import settings
from app.repositories import preference_repo

TODAY = date(2026, 10, 12)


class _Result:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class _FakeSession:
    """按调用顺序吐出预置结果，并记录每次 execute 的参数（通道顺序：订单/收藏/好评/加购/浏览/搜索词）"""

    def __init__(self, *responses):
        self._responses = list(responses)
        self.params = []

    def execute(self, stmt, params=None):
        self.params.append(params)
        return _Result(self._responses.pop(0))

    def close(self):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def test_browse_weights_apply_decay_and_normalize():
    session = _FakeSession([(10, TODAY), (10, TODAY - timedelta(days=3)), (20, TODAY - timedelta(days=6))])
    weights = preference_repo.browse_category_weights(session, 7, today=TODAY)
    # cat10 = 1.0 + 0.5 = 1.5 → 归一后 1.0；cat20 = 0.25 / 1.5
    assert weights[10] == pytest.approx(1.0)
    assert weights[20] == pytest.approx(0.25 / 1.5, abs=1e-6)
    # 回看窗口传参：REC_BROWSE_DAYS=7 → since = today-6
    assert session.params[0]["since"] == TODAY - timedelta(days=settings.REC_BROWSE_DAYS - 1)
    assert session.params[0]["uid"] == 7


def test_browse_weights_empty_for_no_footprint():
    assert preference_repo.browse_category_weights(_FakeSession([]), 7, today=TODAY) == {}


def test_favorite_weights_count_per_row():
    session = _FakeSession([(10, 3)])
    weights = preference_repo.favorite_category_weights(session, 7)
    assert weights == {10: 1.0}


def test_search_weights_only_recent_window():
    session = _FakeSession([(10, 2)])
    weights = preference_repo.search_category_weights(session, 7, today=TODAY)
    assert weights == {10: 1.0}
    # 搜索词回看窗口：REC_SEARCH_SIGNAL_DAYS=7 → since = today-6
    assert session.params[0]["since"] == TODAY - timedelta(days=settings.REC_SEARCH_SIGNAL_DAYS - 1)


def test_preference_with_signals_merges_six_signals():
    """六路合并：订单 0.5 / 收藏 0.35 / 好评 0.3 / 加购 0.3 / 浏览 0.2 / 搜索词 0.15"""
    order_rows = [(10, 4.0)]      # 雪梨类目买过 4 件
    favorite_rows = [(10, 2.0)]   # 同类目收藏 2 件
    review_rows = [(10, 2.0)]     # 同类目 2 条好评
    cart_rows = [(20, 1.0)]       # 茶叶类目加购 1 件
    browse_rows = [(20, TODAY)]   # 只在茶叶类目浏览过（今天）
    search_rows = [(30, 1.0)]     # 搜索词命中粮食类目
    session = _FakeSession(order_rows, favorite_rows, review_rows, cart_rows, browse_rows, search_rows)

    merged, browse = preference_repo.preference_with_signals(session, 7)

    # cat10: 0.5+0.35+0.3=1.15；cat20: 0.3+0.2=0.5；cat30: 0.15 → 归一
    assert merged[10] == pytest.approx(1.0)
    assert merged[20] == pytest.approx(0.5 / 1.15, abs=1e-6)
    assert merged[30] == pytest.approx(0.15 / 1.15, abs=1e-6)
    # 纯浏览信号单列，供"最近看过"理由标签
    assert browse == {20: 1.0}


def test_user_preference_is_merged_only():
    session = _FakeSession([(10, 3.0)], [], [], [], [(10, TODAY), (20, TODAY - timedelta(days=2))], [])
    pref = preference_repo.user_preference(session, 7)
    assert set(pref) == {10, 20}
    assert pref[10] == pytest.approx(1.0)


def test_no_history_returns_empty():
    session = _FakeSession([], [], [], [], [], [])
    merged, browse = preference_repo.preference_with_signals(session, 7)
    assert merged == {}
    assert browse == {}
