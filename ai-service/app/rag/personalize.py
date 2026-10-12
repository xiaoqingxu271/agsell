"""导购个性化重排（混合推荐：语义召回 + 类目偏好 + 销量 + 好评率）

final = sem_score + α·pref(category) + β·sales_norm + γ·positive_rate
- sem_score：商品向量召回相似度（0~1）
- pref：用户类目偏好权重（0~1，订单+好评+衰减浏览三路合并，见 preference_repo）
- sales_norm：候选集内 log10(1+销量) 归一（0~1）
- positive_rate：Java 口碑注入的好评率（0~100 → 0~1；无口碑数据记 0）

游客（无 userId）不叠加偏好项，仅销量/好评微调——结果与纯语义序差异有限，
保持"LLM+规则兜底"式可退化设计：任何一环失败都退回向量召回原序。
"""
import logging
import math

from sqlalchemy.orm import Session

from app.config import settings
from app.repositories import preference_repo, product_repo

logger = logging.getLogger("agsell.ai.personalize")


def sales_norm(products: list[dict]) -> dict[int, float]:
    """候选集内销量归一：log10(1+sales) / max，值域 0~1"""
    if not products:
        return {}
    logs = {p["id"]: math.log10(1 + int(p.get("sales") or 0)) for p in products}
    mx = max(logs.values())
    if mx <= 0:
        return {pid: 0.0 for pid in logs}
    return {pid: v / mx for pid, v in logs.items()}


def final_scores(
    products: list[dict],
    sem_by_id: dict[int, float],
    pref_by_cat: dict[int, float],
    cat_by_id: dict[int, int],
) -> dict[int, float]:
    sn = sales_norm(products)
    out: dict[int, float] = {}
    for p in products:
        pid = p["id"]
        pref = pref_by_cat.get(cat_by_id.get(pid, -1), 0.0)
        rate = p.get("positiveRate")
        rate_norm = float(rate) / 100.0 if rate is not None else 0.0
        out[pid] = (
            sem_by_id.get(pid, 0.0)
            + settings.REC_PREF_ALPHA * pref
            + settings.REC_SALES_BETA * sn.get(pid, 0.0)
            + settings.REC_RATE_GAMMA * rate_norm
        )
    return out


class Personalizer:
    """节点侧依赖封装：单会话读 MySQL（偏好 + 类目映射），纯函数打分便于单测替换"""

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
                pref_by_cat = (
                    preference_repo.user_preference(session, user_id) if user_id else {}
                )
            finally:
                session.close()
            scores = final_scores(products, sem_by_id, pref_by_cat, cat_by_id)
            return sorted(products, key=lambda p: -scores.get(p["id"], 0.0))
        except Exception:
            logger.exception("个性化重排失败，回退向量召回原序")
            return products
