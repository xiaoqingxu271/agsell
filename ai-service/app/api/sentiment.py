"""评价情感分析接口（Java 管理端触发同步时批量调用）

契约（Java `ReviewSentimentServiceImpl`）：
- POST /v1/sentiment/batch  X-Internal-Key 鉴权
  请求：{"items": [{"reviewId": 123, "content": "...", "rating": 5}, ...]}
  响应：{"results": [{"reviewId": 123, "label": 1, "score": 0.92, "keywords": ["新鲜"]}, ...]}
"""
from fastapi import APIRouter, Header
from pydantic import BaseModel, Field

from app.security import check_service_key
from app.sentiment.analyzer import analyze_sentiment

router = APIRouter()


class SentimentItem(BaseModel):
    reviewId: int = Field(..., description="评价 ID")
    content: str = Field(default="", max_length=2000, description="评价内容")
    rating: int | None = Field(default=None, ge=1, le=5, description="评分 1~5（先验）")


class SentimentBatchRequest(BaseModel):
    items: list[SentimentItem] = Field(..., max_length=200, description="待分析评价（单批上限 200 条）")


@router.post("/sentiment/batch")
def sentiment_batch(req: SentimentBatchRequest, x_internal_key: str | None = Header(default=None)):
    check_service_key(x_internal_key)
    results = []
    for item in req.items:
        r = analyze_sentiment(item.content, item.rating)
        results.append({"reviewId": item.reviewId, **r})
    return {"results": results}
