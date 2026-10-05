"""服务间鉴权（Java 主系统 ↔ Python AI 服务）"""
import os

from fastapi import HTTPException

from app.config import settings


def check_service_key(x_internal_key: str | None) -> None:
    """校验 X-Internal-Key 请求头（/v1/chat、/v1/chat/stream、/v1/sentiment/batch 等）。

    ALLOW_ANON_CHAT=1 可放行（仅本地调试用），默认关闭。
    """
    if os.environ.get("ALLOW_ANON_CHAT", "0") == "1":
        return
    if not x_internal_key or x_internal_key != settings.JAVA_INTERNAL_KEY:
        raise HTTPException(status_code=401, detail="无效的服务密钥")
