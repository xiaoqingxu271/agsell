"""首页个性化推荐 feed 与搜索框语义召回（POST /v1/rec/*，X-Internal-Key 鉴权）

两条链路的打分都委托给 rag/personalize.score_products（全站唯一口径），本模块只负责取数：
- feed：无查询场景，语义分恒 0 → α·类目偏好 + β·销量 + γ·好评率 + δ·新鲜度；
- search：商品向量召回 → min score 闸门过滤域外词 → 同规则再叠语义项。
- 类目偏好由订单+好评+最近浏览三路信号合并（浏览带时间衰减，见 preference_repo），
  因此用户看完几件腊味再下拉刷新，首页推荐当场就会转向腊味类目；
- 游客（无 userId）不叠加偏好，等价"销量+口碑+时令"混合榜；
- 每项返回分项贡献 breakdown，支撑"为什么推荐给我"可解释展示与答辩留证。
"""
import logging
from datetime import date

from fastapi import APIRouter, Header
from pydantic import BaseModel, Field

from app.config import settings
from app.db import SessionLocal
from app.rag.freshness import freshness
from app.rag.personalize import score_products
from app.repositories import preference_repo, product_repo, review_repo
from app.security import check_service_key

logger = logging.getLogger("agsell.ai.rec_feed")

router = APIRouter(prefix="/rec", tags=["rec"])


class FeedRequest(BaseModel):
    userId: int | None = Field(default=None, description="登录用户，游客为空")
    limit: int = Field(default=10, ge=1, le=50)


@router.post("/feed")
def rec_feed(req: FeedRequest, x_internal_key: str | None = Header(default=None)):
    check_service_key(x_internal_key)

    with SessionLocal() as session:
        products = product_repo.list_on_sale(session)
        cat_by_id = product_repo.category_by_ids(session, [p.id for p in products])
        rate_by_id, rate_prior = review_repo.positive_rates(session)
        sales_max = product_repo.max_sales(session)
        pref_by_cat, browse_by_cat = (
            preference_repo.preference_with_signals(session, req.userId)
            if req.userId
            else ({}, {})
        )

    today = date.today()
    scored = score_products(
        [{"id": p.id, "name": p.name, "sales": p.sales} for p in products],
        pref_by_cat=pref_by_cat,
        cat_by_id=cat_by_id,
        rate_by_id=rate_by_id,
        rate_prior=rate_prior,
        fresh_by_id={p.id: freshness(p.harvest_date, p.shelf_life, today) for p in products},
        browse_by_cat=browse_by_cat,
        sales_max=sales_max,
    )
    return {
        "userId": req.userId,
        "personalized": bool(req.userId and pref_by_cat),
        "candidates": len(scored),
        "items": scored[: req.limit],
    }


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=50, description="搜索框原始词")
    userId: int | None = Field(default=None, description="登录用户，游客为空")
    limit: int = Field(default=50, ge=1, le=100)


@router.post("/search")
def rec_search(req: SearchRequest, x_internal_key: str | None = Header(default=None)):
    """搜索框语义召回（POST /v1/rec/search）

    向量召回 product 索引 → min score 闸门过滤域外词 → 与 feed 同一套混合重排
    （只多一个语义项），返回语义命中 id 有序集交 Java。
    Java 侧再用在售/分类过滤并与全文/命中结果合并；本接口空 items 表示应退回全文检索。
    """
    check_service_key(x_internal_key)

    from app.main import get_product_store  # 延迟导入避免循环依赖

    hits = get_product_store().search(req.query, top_k=settings.REC_SEARCH_TOP_K)
    sem_by_id = {
        h["id"]: h["score"] for h in hits if h["score"] >= settings.REC_SEARCH_MIN_SCORE
    }
    if not sem_by_id:
        return {
            "query": req.query,
            "userId": req.userId,
            "personalized": False,
            "candidates": 0,
            "minScore": settings.REC_SEARCH_MIN_SCORE,
            "items": [],
        }

    ids = list(sem_by_id)
    with SessionLocal() as session:
        products = product_repo.list_by_ids(session, ids)
        cat_by_id = product_repo.category_by_ids(session, [p.id for p in products])
        rate_by_id, rate_prior = review_repo.positive_rates(session)
        sales_max = product_repo.max_sales(session)
        pref_by_cat, browse_by_cat = (
            preference_repo.preference_with_signals(session, req.userId)
            if req.userId
            else ({}, {})
        )

    today = date.today()
    scored = score_products(
        [{"id": p.id, "name": p.name, "sales": p.sales} for p in products],
        sem_by_id=sem_by_id,
        pref_by_cat=pref_by_cat,
        cat_by_id=cat_by_id,
        rate_by_id=rate_by_id,
        rate_prior=rate_prior,
        fresh_by_id={p.id: freshness(p.harvest_date, p.shelf_life, today) for p in products},
        browse_by_cat=browse_by_cat,
        sales_max=sales_max,
    )
    return {
        "query": req.query,
        "userId": req.userId,
        "personalized": bool(req.userId and pref_by_cat),
        "candidates": len(scored),
        "minScore": settings.REC_SEARCH_MIN_SCORE,
        "items": scored[: req.limit],
    }
