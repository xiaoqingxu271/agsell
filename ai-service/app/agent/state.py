"""LangGraph Agent 状态定义

阶段二：messages + faq_context
阶段三：+ intent / order_no / userId / tool_result / tool_type / human_fallback
"""
from typing import Annotated, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    # 阶段二：RAG 检索
    faq_context: list  # [{id, question, answer, score}]
    # 阶段三：意图路由与工具
    intent: str  # faq/order/logistics/after_sales/smalltalk/human
    order_no: str | None
    userId: int | None
    tool_result: str  # Java 回调结果（JSON 文本），供生成节点使用
    tool_type: str  # order/logistics/after_sales/error
    human_fallback: bool
    # 阶段四：下一轮建议问题（小贴士）
    suggestions: list  # [str, ...]
