"""FastAPI 入口（阶段二 + 阶段五）

- GET  /health                健康检查
- POST /v1/chat               客服对话（LangGraph: faq_retrieve → generate）
- /v1/admin/kb/*              知识库管理（stats/index/faqs）

阶段五：
- 会话记忆：默认 SqliteSaver（本地文件持久化，服务重启后会话可恢复）；
  MEMORY_BACKEND=redis 时尝试 RedisSaver（需 Redis 8+ 内置 RediSearch），失败自动降级 sqlite
- LLM 调用：ChatOpenAI 内置 429/5xx 退避重试；生成节点失败返回降级话术（不白屏）
"""
import logging
from contextlib import asynccontextmanager
from functools import lru_cache
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI

from app.agent.graph import build_graph
from app.api.admin_kb import router as admin_kb_router
from app.config import settings
from app.db import SessionLocal
from app.rag.retriever import Retriever
from app.rag.vectorstore import VectorStore
from app.repositories import faq_repo
from app.schemas.chat import ChatRequest, ChatResponse, HealthResponse

logger = logging.getLogger("agsell.ai")

# 全局 checkpointer：lifespan 中初始化（SqliteSaver / RedisSaver / None→MemorySaver）
_checkpointer = None


def _check_service_key(x_internal_key: str | None) -> None:
    """服务间鉴权：Java 主系统转发 /v1/chat 时携带 X-Internal-Key。

    ALLOW_ANON_CHAT=1 可放行（仅本地调试用），默认关闭。
    """
    import os

    if os.environ.get("ALLOW_ANON_CHAT", "0") == "1":
        return
    if not x_internal_key or x_internal_key != settings.JAVA_INTERNAL_KEY:
        raise HTTPException(status_code=401, detail="无效的服务密钥")


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _checkpointer
    _checkpointer = None

    backend = settings.MEMORY_BACKEND
    try:
        if backend == "redis":
            from langgraph.checkpoint.redis import AsyncRedisSaver

            async with AsyncRedisSaver.from_conn_string(settings.REDIS_URL) as saver:
                await saver.asetup()
                _checkpointer = saver
                logger.info("会话记忆后端：RedisSaver（%s）", settings.REDIS_URL)
                yield
            return
        if backend == "sqlite":
            from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

            # 确保数据目录存在（与向量库同一目录）
            Path(settings.SQLITE_PATH).parent.mkdir(parents=True, exist_ok=True)
            async with AsyncSqliteSaver.from_conn_string(settings.SQLITE_PATH) as saver:
                await saver.setup()
                _checkpointer = saver
                logger.info("会话记忆后端：SqliteSaver（%s）", settings.SQLITE_PATH)
                yield
            return
    except Exception as e:  # 持久化后端初始化失败 → 降级进程内记忆
        _checkpointer = None
        logger.warning("持久化后端 %s 初始化失败，降级 MemorySaver：%s", backend, e)

    # memory / 降级路径
    logger.info("会话记忆后端：MemorySaver（进程内）")
    yield


app = FastAPI(
    title="agsell AI 客服服务",
    description="独立 Python AI 服务：FastAPI + LangGraph + Agnes AI + RAG 知识库",
    version="0.3.0",
    lifespan=lifespan,
)


@lru_cache
def get_llm():
    if not settings.AGNES_API_KEY:
        raise RuntimeError("AGNES_API_KEY 未配置，请检查 .env")
    return ChatOpenAI(
        model=settings.AGNES_MODEL,
        base_url=settings.AGNES_BASE_URL,
        api_key=settings.AGNES_API_KEY,
        temperature=settings.AGNES_TEMPERATURE,
        timeout=settings.LLM_TIMEOUT,
        max_retries=settings.LLM_MAX_RETRIES,  # 429/5xx 自动退避重试
    )


@lru_cache
def get_vectorstore() -> VectorStore:
    return VectorStore(path=settings.VECTOR_DB_PATH, model=settings.EMBEDDING_MODEL)


@lru_cache
def get_retriever() -> Retriever:
    return Retriever(
        vector_store=get_vectorstore(),
        faq_repo=faq_repo,
        session_factory=SessionLocal,
    )


@lru_cache
def get_graph():
    # _checkpointer 为 None 时 build_graph 内部回退 MemorySaver
    return build_graph(get_llm(), get_retriever(), checkpointer=_checkpointer)


app.include_router(admin_kb_router, prefix="/v1")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok", model=settings.AGNES_MODEL)


@app.post("/v1/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, x_internal_key: str | None = Header(default=None)) -> ChatResponse:
    # 阶段五：服务间鉴权——仅允许 Java 主系统（携带 X-Internal-Key）转发；
    # 防止 8000 端口被外部直接调用消耗 Agnes 免费额度
    _check_service_key(x_internal_key)

    try:
        graph = get_graph()
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e)) from e

    try:
        result = await graph.ainvoke(
            {
                "messages": [HumanMessage(content=req.message)],
                "userId": req.userId,
            },
            # 阶段四：以 sessionId 为 thread_id，多轮对话共享上下文
            config={"configurable": {"thread_id": req.sessionId or "default"}},
        )
        # 去除回复首尾空白（Agnes 常带前导换行，避免客户端气泡顶部空白）
        reply = (result["messages"][-1].content or "").strip()
        suggestions = result.get("suggestions") or []
        return ChatResponse(sessionId=req.sessionId, reply=reply, suggestions=suggestions)
    except Exception as e:  # LLM 调用失败等
        logger.exception("AI 服务调用失败")
        raise HTTPException(status_code=502, detail=f"AI 服务调用失败: {e}") from e
