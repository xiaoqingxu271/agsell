"""浏览行为的时间衰减：越近的浏览对偏好贡献越大（指数半衰减）

累加口径（易误读，写死在这里）：
- user_product_view 的唯一键是「用户×商品×自然日」，所以同一天反复刷同一件商品
  只落一条记录，不会自叠加；
- 但同一天看同一类目的**不同**商品，每条记录各计一次衰减权重、会累加——
  这是有意为之：一下午连看三件腊味，比只看一件的意图强得多。
即"同日同商品不叠加、同日不同商品叠加"。
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
