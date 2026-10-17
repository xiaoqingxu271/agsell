"""评价口碑聚合（只读）：按商品好评率 + Bayesian 收缩，口径与 Java ReviewSummaryVO.of 同源

sentiment_label：1=好评 / 0=中评 / 其他=差评；NULL（未分析）不计入分母。

为什么要收缩：本库口碑样本极稀（202 件在售商品仅 7 件有已分析评价、共 12 条），
原始好评率直接进排序有两个硬伤——
- 「1 条差评」= 0% 好评，与「几十条差评」同分，一条评价就能定生死；
- 其余 195 件零评价商品根本查不到，打分端按 0 处理，等于把"没人评价"当成"口碑最差"，
  系统性压掉新商品——而这恰好和"新品/时令品优先"的产品目标相反。
故 rate = (好评数 + m·全站均值) / (评价数 + m)，m=REC_RATE_PRIOR_COUNT；
零评价商品取全站均值（中性），不再取 0。
"""
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import settings


def positive_rates(session: Session) -> tuple[dict[int, float], float]:
    """返回 (收缩后好评率 {product_id: 0~100}, 全站均值 prior 0~100)

    prior 供打分端给"无任何已分析评价"的商品兜底：字典里查不到 ≠ 口碑差。
    全库无已分析评价时 prior 取 0——此时该项对所有商品同为常数，不影响排序。
    """
    rows = session.execute(
        text(
            """
            select product_id,
                   sum(case when sentiment_label = 1 then 1 else 0 end) as pos,
                   count(*) as total
            from review
            where deleted = 0 and sentiment_label is not null
            group by product_id
            """
        )
    ).all()
    counts = [(int(pid), int(pos), int(total)) for pid, pos, total in rows if total]
    total_reviews = sum(t for _, _, t in counts)
    if not total_reviews:
        return {}, 0.0

    prior = sum(p for _, p, _ in counts) * 100.0 / total_reviews
    m = settings.REC_RATE_PRIOR_COUNT
    shrunk = {pid: (pos + m * prior) / (total + m) for pid, pos, total in counts}
    return shrunk, round(prior, 4)
