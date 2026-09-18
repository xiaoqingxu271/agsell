"""Chat 接口 Pydantic 模型（Pydantic v2）"""
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """调用链：小程序 → Java（校验 JWT）→ Python /v1/chat"""

    sessionId: str = Field(..., description="会话 ID，由 Java 侧生成")
    message: str = Field(..., min_length=1, max_length=2000, description="用户消息")
    userId: int | None = Field(default=None, description="用户 ID（阶段三起使用）")


class ChatResponse(BaseModel):
    sessionId: str
    reply: str
    # 阶段四：下一轮建议问题（小贴士），点击可直接发送
    suggestions: list[str] = Field(default_factory=list, description="下一轮建议问题")


class HealthResponse(BaseModel):
    status: str = "ok"
    model: str
    version: str = "0.1.0"
