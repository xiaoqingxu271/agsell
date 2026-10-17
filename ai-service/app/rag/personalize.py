"""混合排序的唯一实现：feed / 语义搜索 / 对话导购三条链路共用同一套口径

    final = 语义分 sem + α·类目偏好 pref + β·销量归一 sales + γ·好评率 rate + δ·新鲜度 fresh

三条链路各自的旧实现曾出现三套公式（feed 缺语义项、导购缺新鲜度项、销量按候选集归一、
零评价按 0 分），同一件商品换个入口就换个名次，答辩时无法解释排序依据。现在：
- 入口差异只体现在"有没有语义分"上（首页 feed 语义分恒 0），权重与量纲完全一致；
- 销量按全库在售最大销量归一（与候选集大小无关）；
- 好评率取 Bayesian 收缩值，零评价商品按全站均值兜底（见 review_repo）；
- 每个分项同时输出"原始信号 0~1"（signals，供可解释理由择优）与"加权贡献"（breakdown，
  score == sum(breakdown)），前端展示与服务端打分不会是两套数；
- signals 另带 rate_conf（0/1）标明口碑是实测还是先验：先验值参与排序没问题，但零评价商品
  不能对外说"好评率高"，理由端要据此抑制口碑标签。

游客（无 userId）不叠加偏好项，其余项仍生效；任何一环失败都退回向量召回原序。
"""
import logging
import math
from datetime import date

from sqlalchemy.orm import Session

from app.config import settings
from app.rag.freshness import freshness
from app.repositories import preference_repo, product_repo, review_repo

logger = logging.getLogger("agsell.ai.personalize")


def sales_norm(products: list[dict], sales_max: int | None) -> dict[int, float]:
    """销量归一（全库固定基准）：log10(1+sales) / log10(1+全库在售最大销量)，值域 0~1

    log10 压长尾：本库销量 65~1597 都在同一量级，线性归一会让销量项几乎拉不开差距，
    而按候选集归一又会让同一条商品在不同入口拿不同分——固定基准 + 对数刻度两者都避开。
    """
    if not products:
        return {}
    base = math.log10(1 + max(int(sales_max or 0), 1))
    out: dict[int, float] = {}
    for p in products:
        value = math.log10(1 + int(p.get("sales") or 0))
        out[p["id"]] = round(min(max(value / base, 0.0), 1.0), 4)
    return out


def score_products(
    candidates: list[dict],
    *,
    sem_by_id: dict[int, float] | None = None,
    pref_by_cat: dict[int, float] | None = None,
    cat_by_id: dict[int, int] | None = None,
    rate_by_id: dict[int, float] | None = None,
    rate_prior: float = 0.0,
    fresh_by_id: dict[int, float] | None = None,
    browse_by_cat: dict[int, float] | None = None,
    sales_max: int | None = None,
) -> list[dict]:
    """打分并按总分降序返回 [{id, name, sem, score, signals, breakdown}]

    candidates 只需 id / name / sales 三个字段，其余信号按 id 从旁路字典取，
    这样三条链路（数据源不同：feed 走 MySQL 全量、导购走 Java 商品 VO）能共用同一函数。
    """
    sem_by_id = sem_by_id or {}
    pref_by_cat = pref_by_cat or {}
    cat_by_id = cat_by_id or {}
    rate_by_id = rate_by_id or {}
    fresh_by_id = fresh_by_id or {}
    browse_by_cat = browse_by_cat or {}

    sn = sales_norm(candidates, sales_max)
    scored: list[dict] = []
    for c in candidates:
        pid = c["id"]
        cat = cat_by_id.get(pid, -1)
        pref = pref_by_cat.get(cat, 0.0)
        # 查不到口碑数据 = 没有评价，取全站均值（中性），而不是"零评价 = 零好评"
        rate = rate_by_id.get(pid, rate_prior) / 100.0
        sem = sem_by_id.get(pid, 0.0)
        fresh = fresh_by_id.get(pid, 0.0)
        parts = {
            "sem": round(sem, 4),
            "pref": round(settings.REC_PREF_ALPHA * pref, 4),
            "sales": round(settings.REC_SALES_BETA * sn.get(pid, 0.0), 4),
            "rate": round(settings.REC_RATE_GAMMA * rate, 4),
            "fresh": round(settings.REC_FRESH_DELTA * fresh, 4),
        }
        scored.append(
            {
                "id": pid,
                "name": c.get("name"),
                "sem": round(sem, 4),
                "score": round(sum(parts.values()), 4),
                # signals：各分项归一后的原始 0~1 信号（未乘权重），供可解释理由择优标签
                "signals": {
                    "sem": round(sem, 4),
                    "pref": round(pref, 4),
                    "sales": sn.get(pid, 0.0),
                    "rate": round(rate, 4),
                    # 好评率证据：1.0=该商品有真实评价，0.0=只拿到全站先验。
                    # 先验是中性常数，拿它宣称"好评率高"属于虚假口碑，理由标签必须据此抑制它。
                    "rate_conf": 1.0 if pid in rate_by_id else 0.0,
                    "fresh": round(fresh, 4),
                    # browse 已作为第三路信号并进 pref，此处单列只供"最近看过"标签判定
                    "browse": round(browse_by_cat.get(cat, 0.0), 4),
                },
                # breakdown：各分项对总分的加权贡献，score == sum(breakdown)
                "breakdown": parts,
            }
        )
    scored.sort(key=lambda x: -x["score"])
    return scored


def fresh_from_harvest(harvest_by_id: dict[int, tuple], today) -> dict[int, float]:
    """{id: (harvest_date, shelf_life)} → {id: 新鲜度 0~1}"""
    return {
        pid: freshness(harvest, shelf, today)
        for pid, (harvest, shelf) in harvest_by_id.items()
    }


class Personalizer:
    """节点侧依赖封装：单会话读 MySQL（偏好 + 类目 + 口碑 + 采摘日期），纯函数打分便于单测替换"""

    def __init__(self, session_factory):
        self._session_factory = session_factory

    def rerank(self, user_id: int | None, products: list[dict], sem_by_id: dict[int, float]) -> list[dict]:
        """返回重排后的商品列表；任何异常回退向量召回原序（不中断导购链路）"""
        if not products:
            return products
        try:
            session: Session = self._session_factory()
            try:
                ids = [p["id"] for p in products]
                cat_by_id = product_repo.category_by_ids(session, ids)
                pref_by_cat = preference_repo.user_preference(session, user_id) if user_id else {}
                rate_by_id, rate_prior = review_repo.positive_rates(session)
                fresh_by_id = fresh_from_harvest(
                    product_repo.harvest_by_ids(session, ids), date.today()
                )
                sales_max = product_repo.max_sales(session)
            finally:
                session.close()
            scored = score_products(
                [{"id": p["id"], "name": p.get("name"), "sales": p.get("sales")} for p in products],
                sem_by_id=sem_by_id,
                pref_by_cat=pref_by_cat,
                cat_by_id=cat_by_id,
                rate_by_id=rate_by_id,
                rate_prior=rate_prior,
                fresh_by_id=fresh_by_id,
                sales_max=sales_max,
            )
            by_id = {p["id"]: p for p in products}
            return [by_id[item["id"]] for item in scored if item["id"] in by_id]
        except Exception:
            logger.exception("个性化重排失败，回退向量召回原序")
            return products
