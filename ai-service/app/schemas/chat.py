"""Chat 接口 Pydantic 模型（Pydantic v2）"""
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """调用链：小程序 → Java（校验 JWT）→ Python /v1/chat"""

    sessionId: str = Field(..., description="会话 ID，由 Java 侧生成")
    message: str = Field(..., min_length=1, max_length=2000, description="用户消息")
    userId: int | None = Field(default=None, description="用户 ID（阶段三起使用）")
    productId: int | None = Field(
        default=None,
        description="从商品详情页进客服时携带的商品 ID；Python 侧会复核该商品仅在售才作为问答上下文，下架即忽略",
    )


class ChatResponse(BaseModel):
    sessionId: str
    reply: str
    # 阶段四：下一轮建议问题（小贴士），点击可直接发送
    suggestions: list[str] = Field(default_factory=list, description="下一轮建议问题")
    # 导购推荐的结构化商品卡（与流式接口的 items 事件同口径），供前端渲染可点击卡片
    items: list[dict] = Field(default_factory=list, description="推荐商品卡；非导购分支为空")
    # 知识库命中条目（id+问句，最多 4 条）：前端在气泡下渲染"依据"标签。
    # 引用由服务端结构化下发、不经模型复述，是反幻觉的可核对证据链。
    references: list[dict] = Field(default_factory=list, description="知识库引用；非知识问答为空")


class HealthResponse(BaseModel):
    status: str = "ok"
    model: str
    version: str = "0.1.0"
    # 会话记忆后端与降级标记：sqlite 配置初始化失败会静默退回进程内 MemorySaver
    # （重启丢多轮记忆），此前没有任何可观测信号，演示前只能翻日志
    memory_backend: str = "unknown"
    memory_degraded: bool = False
