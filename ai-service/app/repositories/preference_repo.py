"""用户偏好聚合（只读）：类目偏好权重，供导购个性化重排

数据源三类信号（同一 MySQL 主库，ai-service 只读不写）：
- 订单：order_item × product 类目，非取消订单按件数计权（已验证的真实选择）；
- 好评：rating>=4 的评价按商品类目计数；
- 浏览：user_product_view 近 REC_BROWSE_DAYS 天足迹（「用户×商品×自然日」去重），
  逐条做指数时间衰减后按类目累加（半衰期 REC_BROWSE_HALF_LIFE），
  当天浏览权重最高——所以"刚看过几件腊味"能在一次下拉刷新内改变推荐排序。
各自归一化后按 REC_ORDER_WEIGHT / REC_REVIEW_WEIGHT / REC_BROWSE_WEIGHT 合并，
输出 {category_id: 0~1}。
"""
from datetime import date, timedelta

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import settings
from app.rag import recency

# 浏览足迹单次读取上限（行）：已是 用户×商品×日 粒度，7 天窗口内正常远低于此
BROWSE_ROWS_LIMIT = 500


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


def browse_category_weights(session: Session, user_id: int, today: date | None = None) -> dict[int, float]:
    """近 REC_BROWSE_DAYS 天的浏览足迹 → {category_id: 衰减后权重 0~1}

    累加与衰减的具体口径只有一个实现（rag/recency.py）：表按「用户×商品×自然日」去重，
    故同日反复刷同一件商品不叠加，而同日看同类目的不同商品会各自累加。
    """
    day = today or date.today()
    since = day - timedelta(days=settings.REC_BROWSE_DAYS - 1)
    rows = session.execute(
        text(
            """
            select category_id, view_date
            from user_product_view
            where user_id = :uid and view_date >= :since
            order by view_date desc
            limit :lim
            """
        ),
        {"uid": user_id, "since": since, "lim": BROWSE_ROWS_LIMIT},
    ).all()
    return _normalize(
        recency.browse_category_weights(rows, day, settings.REC_BROWSE_HALF_LIFE)
    )


def preference_with_browse(
    session: Session, user_id: int
) -> tuple[dict[int, float], dict[int, float]]:
    """一次查库返回 (合并偏好权重, 纯浏览权重)。

    纯浏览权重不并入排序、仅供可解释理由判定"最近看过「某类目」"——
    它已作为第三路信号进了 merged，重复计入会让 score 与 breakdown 不一致。
    """
    today = date.today()
    ow = order_category_weights(session, user_id)
    rw = review_category_weights(session, user_id)
    bw = browse_category_weights(session, user_id, today)
    merged: dict[int, float] = {}
    for cat in set(ow) | set(rw) | set(bw):
        merged[cat] = (
            settings.REC_ORDER_WEIGHT * ow.get(cat, 0.0)
            + settings.REC_REVIEW_WEIGHT * rw.get(cat, 0.0)
            + settings.REC_BROWSE_WEIGHT * bw.get(cat, 0.0)
        )
    return _normalize(merged), bw


def user_preference(session: Session, user_id: int) -> dict[int, float]:
    """合并订单、好评、浏览三路信号，返回 {category_id: 权重 0~1}；无历史返回 {}"""
    return preference_with_browse(session, user_id)[0]


def _normalize(weights: dict[int, float]) -> dict[int, float]:
    if not weights:
        return {}
    mx = max(weights.values())
    return {k: (v / mx if mx > 0 else 0.0) for k, v in weights.items()}
