"""Java 主系统内部接口客户端（httpx 回调 agsell）

契约见 agsell `AiInternalController`：
- POST /api/ai/internal/order/list        {userId, status?}  → 最近订单列表
- POST /api/ai/internal/order/detail      {userId, orderNo}  → 订单详情+物流（校验归属）
- POST /api/ai/internal/after-sales/list  {userId, status?}  → 最近售后列表
- POST /api/ai/internal/product/search    {keyword?, minPrice?, maxPrice?, limit?} → 导购商品列表
统一响应：{code: 0, data: ..., message: "ok"}，code != 0 视为失败。
"""
import json
import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger("agsell.ai.java_api")


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


def create_human_ticket(
    user_id: int | None, session_id: str, question: str, reason: str
) -> bool:
    """转人工工单落库：POST /ai/internal/ticket（Java 侧 ai_ticket 表，管理后台「AI 工单」页处理）。

    只在 escalate_human 真正触发后调用；失败由调用方记日志吞掉——工单丢了不该影响对话回复。
    """
    payload: dict = {
        "sessionId": session_id,
        "question": (question or "").strip()[:500],
        "reason": (reason or "").strip()[:255] or None,
    }
    if user_id is not None:
        payload["userId"] = user_id
    try:
        _post("/ai/internal/ticket", payload)
        return True
    except JavaApiError as e:
        logger.warning("转人工工单落库失败（忽略）：sessionId=%s, %s", session_id, e)
        return False


def search_products(
    keyword: str | None = None,
    min_price: float | None = None,
    max_price: float | None = None,
    limit: int = 6,
    ids: list[int] | None = None,
) -> list[dict]:
    """智能导购商品搜索。

    - ids：按向量召回命中的商品 id 批量取详情（Java 侧仍过滤仅在售、可叠加价格区间）；
    - 无 ids 时按 keyword LIKE 搜索；两者皆无按销量降序返回热销商品。
    """
    payload: dict = {"limit": limit}
    if ids:
        payload["productIds"] = ids
    if keyword:
        payload["keyword"] = keyword
    if min_price is not None:
        payload["minPrice"] = min_price
    if max_price is not None:
        payload["maxPrice"] = max_price
    data = _post("/ai/internal/product/search", payload)
    return data or []


def fetch_product(product_id: int) -> dict | None:
    """取单个在售商品的事实（商详页进客服时的上下文来源）。

    复用导购搜索接口的 ids 通道：Java 侧已过滤"仅在售"，因此下架/不存在的商品返回 None，
    聊天页随之退回到"无商品上下文"的常规问答，不会拿着一个买不到的商品跟用户聊。
    """
    if not product_id:
        return None
    items = search_products(ids=[product_id], limit=1)
    return items[0] if items else None


def reputation_text(item: dict | None) -> str:
    """把口碑字段拼成一句可直接呈现的话——改进项 #22 的落点。

    为什么必须由服务端拼：实测模型会把真数字安上错的指标名（66.67% 说成"口碑不错"、
    topKeywords 的"回购"说成"回购率"、sales=1046 说成"1046 人回购"）。提示词禁句能压住
    具体词，压不住同义改写。把这句话做成**唯一来源的成品文本**，卡片与作答都只能引用它，
    形容词就越界不了。

    诚实口径（与个性化侧 rate_conf 一致）：
    - 零评价/缺字段 → "暂无评价数据"，绝不拿全站先验当实测好评率；
    - 有评价 → 只陈述条数与好评率数值，不写"口碑好/高"这类结论；
    - topKeywords 是评价文本里的词，用"买家提到"引出，不写成指标。
    """
    if not item:
        return "暂无评价数据"
    count = item.get("reviewCount")
    try:
        count = int(count) if count is not None else 0
    except (TypeError, ValueError):
        count = 0
    if count <= 0:
        return "暂无评价数据"

    sentence = f"{count} 条买家评价"
    rate = item.get("positiveRate")
    try:
        rate_val = float(rate) if rate is not None else None
    except (TypeError, ValueError):
        rate_val = None
    if rate_val is not None:
        sentence = f"{sentence}，好评率 {round(rate_val, 1)}%"
    keywords = item.get("topKeywords") or []
    picked = [str(k).strip() for k in keywords if str(k).strip()][:3]
    if picked:
        sentence = f"{sentence}，买家提到：{'、'.join(picked)}"
    return sentence


def annotate_reputation(items: list[dict] | None) -> list[dict]:
    """给每个候选商品补一句服务端拼好的口碑话术（卡片字段与 LLM 资料同源）。

    就地写入副本而不是改原对象：Java 返回的 VO 还会被其他地方（热销兜底、重排）复用，
    污染它会让后续比较失去意义。
    """
    out = []
    for it in items or []:
        if not isinstance(it, dict):
            out.append(it)
            continue
        enriched = dict(it)
        enriched["reputation"] = reputation_text(it)
        out.append(enriched)
    return out


def format_product_facts(item: dict) -> str:
    """商品 VO → 给 LLM 看的一行事实（严禁编造的锚点，也是"这个"的指代对象）。

    只带可核对的字段：名称/价格/产地/销量/采摘日期/储存/保质期/口碑。
    口碑一律走 reputation_text 的成品句子，这里不再自己组织措辞。
    """
    if not item:
        return ""
    parts = [f"名称：{item.get('name')}"]
    if item.get("price") is not None:
        parts.append(f"售价：{item.get('price')} 元")
    if item.get("subtitle"):
        parts.append(f"卖点：{item.get('subtitle')}")
    if item.get("origin"):
        parts.append(f"产地：{item.get('origin')}")
    if item.get("harvestDate"):
        parts.append(f"采摘/收获日期：{item.get('harvestDate')}")
    if item.get("storage"):
        parts.append(f"储存方式：{item.get('storage')}")
    if item.get("shelfLife"):
        parts.append(f"保质期：{item.get('shelfLife')}")
    if item.get("sales") is not None:
        parts.append(f"销量：{item.get('sales')}")
    parts.append(f"口碑：{reputation_text(item)}")
    return "｜".join(str(p) for p in parts)


def format_payload(data: Any) -> str:
    """把工具结果转成给 LLM 看的紧凑 JSON 文本"""
    if data is None:
        return "（无数据）"
    if isinstance(data, str):
        return data
    return json.dumps(data, ensure_ascii=False, default=str)
