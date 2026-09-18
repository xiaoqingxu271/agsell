"""LangGraph 图构建（阶段三 + 阶段四）

START → intent → 条件路由：
  faq        → faq_retrieve → generate → END
  order      → order_query → generate → END
  logistics  → logistics_query → generate → END
  after_sales→ after_sales_query → generate → END
  smalltalk  → smalltalk → END
  human      → human_fallback → END

阶段四：支持 checkpointer（MemorySaver，按 sessionId/thread_id 维持多轮上下文）。
"""
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from app.agent import nodes
from app.agent.nodes import (
    make_after_sales_query_node,
    make_faq_retrieve_node,
    make_generate_node,
    make_human_fallback_node,
    make_intent_node,
    make_logistics_query_node,
    make_order_query_node,
    make_smalltalk_node,
    route_by_intent,
)
from app.agent.state import AgentState

# 哨兵：区分"未传"（默认 MemorySaver）与"显式传 None"（关闭多轮记忆，单测用）
_DEFAULT_CHECKPOINTER = object()


def build_graph(llm, retriever=None, java_api_module=None, checkpointer=_DEFAULT_CHECKPOINTER):
    """构建并编译客服 Agent 图。llm/retriever/java_api_module 可传真实实现或测试 stub。

    checkpointer: 默认 MemorySaver（进程内多轮记忆）；单测传 None 关闭。
    """
    builder = StateGraph(AgentState)
    builder.add_node("intent", make_intent_node(llm))
    builder.add_node("faq_retrieve", make_faq_retrieve_node(retriever))
    builder.add_node("order_query", make_order_query_node(java_api_module))
    builder.add_node("logistics_query", make_logistics_query_node(java_api_module))
    builder.add_node("after_sales_query", make_after_sales_query_node(java_api_module))
    builder.add_node("generate", make_generate_node(llm))
    builder.add_node("smalltalk", make_smalltalk_node(llm))
    builder.add_node("human_fallback", make_human_fallback_node())

    builder.add_edge(START, "intent")
    builder.add_conditional_edges("intent", route_by_intent)
    builder.add_edge("faq_retrieve", "generate")
    builder.add_edge("order_query", "generate")
    builder.add_edge("logistics_query", "generate")
    builder.add_edge("after_sales_query", "generate")
    builder.add_edge("generate", END)
    builder.add_edge("smalltalk", END)
    builder.add_edge("human_fallback", END)
    # 阶段四：默认带 MemorySaver，按 thread_id（sessionId）维持多轮上下文
    if checkpointer is _DEFAULT_CHECKPOINTER:
        checkpointer = MemorySaver()
    return builder.compile(checkpointer=checkpointer)
