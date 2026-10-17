"""LangGraph Agent 状态定义

阶段二：messages + faq_context
阶段三：+ intent / order_no / userId / tool_result / tool_type / human_fallback
阶段六：+ 智能导购（recommend 意图 → 商品搜索 → 推荐话术）
本轮：+ 导购偏好随意图一次产出（rec_keyword / rec_max_price / rec_in_domain）
"""
from typing import Annotated, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    # 阶段二：RAG 检索
    faq_context: list  # [{id, question, answer, score}]
    # 阶段三：意图路由与工具
    intent: str  # faq/order/logistics/after_sales/smalltalk/human/recommend
    order_no: str | None
    userId: int | None
    tool_result: str  # Java 回调结果（JSON 文本），供生成节点使用
    tool_type: str  # order/logistics/after_sales/recommend/recommend_refused/error
    human_fallback: bool
    # 导购偏好：与意图同一次 LLM 调用产出，省掉导购分支的一次串行往返。
    # None = 没拿到大模型判定（规则短路或故障兜底），导购节点据此退回原有三级降级。
    rec_keyword: str | None
    rec_max_price: float | None
    rec_in_domain: bool | None
    # 阶段四：下一轮建议问题（小贴士）
    suggestions: list  # [str, ...]
