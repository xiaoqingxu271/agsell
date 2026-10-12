"""评价口碑聚合（只读）：按商品好评率，口径与 Java ReviewSummaryVO.of 一致

sentiment_label：1=好评 / 0=中评 / 其他=差评；NULL（未分析）不计入分母。
"""
from sqlalchemy import text
from sqlalchemy.orm import Session


def positive_rates(session: Session) -> dict[int, float]:
    """{product_id: 好评率 0~100}，仅含有已分析评价的商品"""
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
    return {int(pid): float(pos) * 100.0 / float(total) for pid, pos, total in rows if total}
