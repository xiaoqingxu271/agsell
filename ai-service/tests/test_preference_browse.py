"""偏好聚合的浏览信号测试（假 Session，不打真库）

覆盖：SQL 参数窗口、衰减后归一、三路信号合并权重、merged 与纯浏览分别返回。
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
    """按调用顺序吐出预置结果，并记录每次 execute 的参数"""

    def __init__(self, *responses):
        self._responses = list(responses)
        self.params = []

    def execute(self, stmt, params=None):
        self.params.append(params)
        return _Result(self._responses.pop(0))


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


def test_preference_with_browse_merges_three_signals():
    """订单 0.5 / 好评 0.3 / 浏览 0.2 合并，并同时返回纯浏览强度"""
    order_rows = [(10, 4.0)]      # 雪梨类目买过 4 件
    review_rows = [(10, 2.0)]     # 同类目 2 条好评
    browse_rows = [(20, TODAY)]   # 只在茶叶类目浏览过（今天）
    session = _FakeSession(order_rows, review_rows, browse_rows)

    merged, browse = preference_repo.preference_with_browse(session, 7)

    # cat10: 0.5*1 + 0.3*1 = 0.8；cat20: 0.2*1 = 0.2 → 归一 cat10=1.0, cat20=0.25
    assert merged[10] == pytest.approx(1.0)
    assert merged[20] == pytest.approx(0.25, abs=1e-6)
    # 纯浏览信号单列，供"最近看过"理由标签
    assert browse == {20: 1.0}


def test_user_preference_is_merged_only():
    session = _FakeSession([(10, 3.0)], [], [(10, TODAY), (20, TODAY - timedelta(days=2))])
    pref = preference_repo.user_preference(session, 7)
    assert set(pref) == {10, 20}
    assert pref[10] == pytest.approx(1.0)


def test_no_history_returns_empty():
    session = _FakeSession([], [], [])
    merged, browse = preference_repo.preference_with_browse(session, 7)
    assert merged == {}
    assert browse == {}
