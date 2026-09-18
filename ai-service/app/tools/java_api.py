"""Java 主系统内部接口客户端（httpx 回调 agsell）

契约见 agsell `AiInternalController`：
- POST /api/ai/internal/order/list        {userId, status?}  → 最近订单列表
- POST /api/ai/internal/order/detail      {userId, orderNo}  → 订单详情+物流（校验归属）
- POST /api/ai/internal/after-sales/list  {userId, status?}  → 最近售后列表
统一响应：{code: 0, data: ..., message: "ok"}，code != 0 视为失败。
"""
import json
from typing import Any

import httpx

from app.config import settings


class JavaApiError(Exception):
    """Java 内部接口调用失败（网络/业务错误）"""


def _post(path: str, payload: dict) -> Any:
    url = settings.JAVA_BASE_URL.rstrip("/") + path
    headers = {"X-Internal-Key": settings.JAVA_INTERNAL_KEY, "Content-Type": "application/json"}
    try:
        resp = httpx.post(url, json=payload, headers=headers, timeout=8.0)
        resp.raise_for_status()
    except (httpx.HTTPError, httpx.TimeoutException) as e:
        raise JavaApiError(f"无法连接 Java 主系统（{url}）：{e}") from e

    body = resp.json()
    if body.get("code") != 0:
        raise JavaApiError(f"Java 返回错误：{body.get('message', '未知错误')}")
    return body.get("data")


def query_orders(user_id: int, status: int | None = None) -> list[dict]:
    """查询用户最近订单列表"""
    data = _post("/ai/internal/order/list", {"userId": user_id, "status": status})
    return data or []


def query_order_detail(user_id: int, order_no: str) -> dict:
    """查询单个订单详情（含物流与商品快照）"""
    data = _post("/ai/internal/order/detail", {"userId": user_id, "orderNo": order_no})
    return data or {}


def query_logistics(user_id: int, order_no: str) -> dict:
    """查询物流信息（复用订单详情，仅抽取物流字段）"""
    detail = query_order_detail(user_id, order_no)
    if not detail:
        return {}
    return {
        "orderNo": detail.get("orderNo"),
        "status": detail.get("status"),
        "statusText": detail.get("statusText"),
        "logType": detail.get("logType"),
        "logNo": detail.get("logNo"),
        "deliveryTime": detail.get("deliveryTime"),
        "receiveTime": detail.get("receiveTime"),
    }


def query_after_sales(user_id: int, status: int | None = None) -> list[dict]:
    """查询用户最近售后/退款记录"""
    data = _post("/ai/internal/after-sales/list", {"userId": user_id, "status": status})
    return data or []


def format_payload(data: Any) -> str:
    """把工具结果转成给 LLM 看的紧凑 JSON 文本"""
    if data is None:
        return "（无数据）"
    if isinstance(data, str):
        return data
    return json.dumps(data, ensure_ascii=False, default=str)
