"""浏览行为的时间衰减：越近的浏览对偏好贡献越大（指数半衰减）

同日重复浏览不叠加：user_product_view 表粒度已是「用户×商品×自然日」，
这里每条 (类目, 日期) 只计一次衰减权重，避免反复刷同一商品把偏好自推成独占。
"""
from datetime import date


def decay_weight(age_days: int, half_life_days: int) -> float:
    """age=0 → 1.0；age=half_life → 0.5；半衰期 <=0 视为不衰减（恒 1.0）"""
    if half_life_days <= 0:
        return 1.0
    return 0.5 ** (max(age_days, 0) / half_life_days)


def browse_category_weights(
    rows: list[tuple[int, date]],
    today: date,
    half_life_days: int,
) -> dict[int, float]:
    """把 [(category_id, view_date)] 聚合成 {category_id: 衰减后累计权重}"""
    out: dict[int, float] = {}
    for category_id, view_date in rows:
        if view_date is None:
            continue
        age_days = (today - view_date).days
        out[category_id] = out.get(category_id, 0.0) + decay_weight(age_days, half_life_days)
    return out
