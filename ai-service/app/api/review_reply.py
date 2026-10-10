"""评价智能回复接口（Java 管理端「AI 生成回复」按钮调用）

契约（Java `AdminReviewServiceImpl.generateAiReply`）：
- POST /v1/review/reply  X-Internal-Key 鉴权
  请求：{"reviewId": 123, "content": "...", "rating": 4, "sentimentLabel": -1, "productName": "赣南脐橙"}
  响应：{"reviewId": 123, "reply": "亲，...", "source": "llm" | "fallback"}
"""
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app.config import settings
from app.review_reply.generator import generate_reply
from app.security import check_service_key

router = APIRouter()


class ReviewReplyRequest(BaseModel):
    reviewId: int | None = Field(default=None, description="评价 ID（原样回传）")
    content: str = Field(default="", max_length=2000, description="评价内容")
    rating: int | None = Field(default=None, ge=1, le=5, description="评分 1~5")
    sentimentLabel: int | None = Field(default=None, ge=-1, le=1, description="口碑标签 1/0/-1（未分析为 null）")
    productName: str = Field(default="", max_length=200, description="商品名称")


@router.post("/review/reply")
def review_reply(req: ReviewReplyRequest, x_internal_key: str | None = Header(default=None)):
    check_service_key(x_internal_key)

    from app.main import get_llm  # 延迟导入避免与 main 的路由注册循环依赖

    try:
        llm = get_llm()
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e)) from e

    result = generate_reply(
        content=req.content,
        rating=req.rating,
        sentiment_label=req.sentimentLabel,
        product_name=req.productName,
        llm=llm,
        platform=settings.PLATFORM_NAME,
        service_phone=settings.SERVICE_PHONE,
    )
    return {"reviewId": req.reviewId, **result}
