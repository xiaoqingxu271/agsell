"""时令/新鲜度引擎：按"剩余保鲜期比例"给商品打新鲜度分（0~1）

对每个商品：fresh = clamp((shelf_life_days - age_days) / shelf_life_days, 0, 1)
- age_days = 参考今日 - harvest_date（采摘/出厂日期）；
- shelf_life_days 由 shelf_life 文案解析（"10天"/"45天"/"12个月"/"18个月"/"1年"…）。

这样能自然区分"现摘生鲜 vs 库陈货 vs 耐储新品"：
- 短保生鲜放久 → 趋 0；耐储粮油/干货/茶即便隔季仍有较高剩余期 → 高新鲜分。
- 无 harvest_date 或无 shelf_life → 记 0（不给新鲜加成，也不惩罚）。
"""
import re
from datetime import date

_UNIT_DAYS = {"天": 1, "日": 1, "周": 7, "月": 30, "年": 365}
_NUM = re.compile(r"(\d+)")


def shelf_life_days(shelf_life: str | None) -> int | None:
    """把保质期文案解析为天数；无法解析返回 None"""
    if not shelf_life:
        return None
    m = _NUM.search(shelf_life)
    if not m:
        return None
    n = int(m.group(1))
    for unit, days in _UNIT_DAYS.items():
        if unit in shelf_life:
            return n * days
    return n  # 纯数字无单位：按天理解


def freshness(harvest: date | None, shelf_life: str | None, today: date) -> float:
    """剩余保鲜期比例（0~1）；缺数据记 0，过期/陈货记 0"""
    if harvest is None:
        return 0.0
    days = shelf_life_days(shelf_life)
    if not days or days <= 0:
        return 0.0
    age = (today - harvest).days
    if age < 0:
        age = 0
    remaining = (days - age) / days
    return round(max(0.0, min(1.0, remaining)), 4)
