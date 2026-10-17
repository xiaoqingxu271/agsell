"""浏览行为时间衰减：半衰期语义、同日/跨日累计口径、边界值"""
from datetime import date, timedelta

import pytest

from app.config import settings
from app.rag.recency import browse_category_weights, decay_weight

TODAY = date(2026, 10, 12)


def _approx(value):
    return pytest.approx(value, abs=1e-9)


def test_decay_anchor_points():
    assert decay_weight(0, 3) == _approx(1.0)
    assert decay_weight(3, 3) == _approx(0.5)
    assert decay_weight(6, 3) == _approx(0.25)


def test_negative_age_treated_as_today():
    # 时钟偏差/未来日期不该把权重放大
    assert decay_weight(-2, 3) == _approx(1.0)


def test_half_life_non_positive_means_no_decay():
    assert decay_weight(30, 0) == _approx(1.0)


def test_same_category_across_days_accumulates():
    rows = [(10, TODAY), (10, TODAY - timedelta(days=3))]
    out = browse_category_weights(rows, TODAY, half_life_days=3)
    assert out[10] == _approx(1.5)


def test_same_day_different_products_accumulate():
    """同日看同类目的不同商品各自累加：连看三件腊味的意图强于只看一件（表按商品×日落行）"""
    rows = [(10, TODAY), (10, TODAY), (10, TODAY)]
    out = browse_category_weights(rows, TODAY, half_life_days=3)
    assert out[10] == _approx(3.0)


def test_repeat_view_of_same_product_never_self_multiplies():
    """同日反复刷同一件商品由 user_product_view 唯一键压成一行，衰减层只会收到一条记录"""
    rows = [(10, TODAY)]  # 当天刷 5 次 → SQL 里仍只有这一行
    out = browse_category_weights(rows, TODAY, half_life_days=3)
    assert out[10] == _approx(1.0)


def test_recent_browse_outranks_old_browse():
    rows = [
        (10, TODAY),                      # 腊味：今天看过
        (20, TODAY - timedelta(days=6)),  # 茶叶：6 天前看过
    ]
    out = browse_category_weights(rows, TODAY, half_life_days=3)
    assert out[10] > out[20]
    assert out[20] == _approx(0.5 ** (6 / 3))


def test_none_view_date_skipped():
    assert browse_category_weights([(10, None)], TODAY, 3) == {}


def test_default_config_values():
    # 答辩口径：回看 7 天、半衰期 3 天、浏览权重低于订单
    assert settings.REC_BROWSE_DAYS == 7
    assert settings.REC_BROWSE_HALF_LIFE == 3
    assert settings.REC_BROWSE_WEIGHT < settings.REC_ORDER_WEIGHT
