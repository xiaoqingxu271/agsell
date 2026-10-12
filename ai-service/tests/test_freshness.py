"""时令/新鲜度引擎单测：保质期文案解析 + 剩余保鲜期比例"""
from datetime import date

from app.rag.freshness import freshness, shelf_life_days

TODAY = date(2026, 10, 12)


def test_shelf_life_days_parsing():
    assert shelf_life_days("10天") == 10
    assert shelf_life_days("45天") == 45
    assert shelf_life_days("7天") == 7
    assert shelf_life_days("3周") == 21
    assert shelf_life_days("12个月") == 360
    assert shelf_life_days("18个月") == 540
    assert shelf_life_days("1年") == 365
    assert shelf_life_days("60") == 60  # 无单位按天
    assert shelf_life_days(None) is None
    assert shelf_life_days("常温") is None  # 无数字
    assert shelf_life_days("") is None


def test_freshness_recent_harvest_full_score():
    from datetime import timedelta

    assert freshness(TODAY, "7天", TODAY) == 1.0
    # 采摘于 1 天前，保质期 7 天 → 剩余约 6/7
    assert freshness(TODAY - timedelta(days=1), "7天", TODAY) == round(6 / 7, 4)


def test_freshness_expired_is_zero():
    # 短保青菜采摘于 30 天前，保质期仅 7 天 → 已过期记 0
    from datetime import timedelta

    assert freshness(TODAY - timedelta(days=30), "7天", TODAY) == 0.0


def test_freshness_long_shelf_ages_slowly():
    from datetime import timedelta

    # 陈茶隔了 60 天，保质期 18 个月(540天) → 剩余比例仍很高
    val = freshness(TODAY - timedelta(days=60), "18个月", TODAY)
    assert 0.85 < val < 0.9


def test_freshness_missing_data_zero():
    assert freshness(None, "12个月", TODAY) == 0.0
    assert freshness(TODAY, None, TODAY) == 0.0
    assert freshness(TODAY, "常温", TODAY) == 0.0


def test_freshness_future_harvest_clamped():
    from datetime import timedelta

    # 采摘日期在未来（数据异常）→ age 记 0，不超过 1
    assert freshness(TODAY + timedelta(days=5), "30天", TODAY) == 1.0
