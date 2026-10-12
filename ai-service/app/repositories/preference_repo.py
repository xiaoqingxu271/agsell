"""用户偏好聚合（只读）：类目偏好权重，供导购个性化重排

数据源两类信号（同一 MySQL 主库，ai-service 只读不写）：
- 订单：order_item × product 类目，近 50 笔非取消订单，按件数计权；
- 好评：rating>=4 的评价按商品类目计数。
各自归一化后按 REC_ORDER_WEIGHT / REC_REVIEW_WEIGHT 合并，输出 {category_id: 0~1}。
"""
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import settings


def order_category_weights(session: Session, user_id: int) -> dict[int, float]:
    rows = session.execute(
        text(
            """
            select p.category_id as cat, sum(oi.quantity) as w
            from `order` o
            join order_item oi on oi.order_id = o.id
            join product p on p.id = oi.product_id
            where o.user_id = :uid and o.status in (1, 2, 3) and o.deleted = 0
              and p.deleted = 0
            group by p.category_id
            order by w desc
            limit 50
            """
        ),
        {"uid": user_id},
    ).all()
    return _normalize({int(r[0]): float(r[1]) for r in rows})


def review_category_weights(session: Session, user_id: int) -> dict[int, float]:
    rows = session.execute(
        text(
            """
            select p.category_id as cat, count(*) as w
            from review r
            join product p on p.id = r.product_id
            where r.user_id = :uid and r.rating >= 4 and r.deleted = 0 and p.deleted = 0
            group by p.category_id
            order by w desc
            limit 50
            """
        ),
        {"uid": user_id},
    ).all()
    return _normalize({int(r[0]): float(r[1]) for r in rows})


def user_preference(session: Session, user_id: int) -> dict[int, float]:
    """合并订单与好评两路信号，返回 {category_id: 权重 0~1}；无历史返回 {}"""
    ow = order_category_weights(session, user_id)
    rw = review_category_weights(session, user_id)
    merged: dict[int, float] = {}
    for cat in set(ow) | set(rw):
        merged[cat] = (
            settings.REC_ORDER_WEIGHT * ow.get(cat, 0.0)
            + settings.REC_REVIEW_WEIGHT * rw.get(cat, 0.0)
        )
    return _normalize(merged)


def _normalize(weights: dict[int, float]) -> dict[int, float]:
    if not weights:
        return {}
    mx = max(weights.values())
    return {k: (v / mx if mx > 0 else 0.0) for k, v in weights.items()}
