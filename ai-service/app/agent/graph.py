"""LangGraph 图构建（智能体版）

START → intent（仅高置信规则前置，零 LLM）→ 条件路由：
  smalltalk     → smalltalk → END          纯寒暄，不必取数
  human_fallback→ human_fallback → END     强诉求，直接转人工话术
  agent         → agent ⇄ tools → generate → END

agent 节点用 llm.bind_tools 自己决定调用哪些工具（可在一轮内并行多个），最多
settings.AGENT_MAX_STEPS 步、单轮最多 settings.AGENT_MAX_TOOL_CALLS 次调用，
并按 (工具, 参数) 去重防止模型不收敛时原地打转；空结果作为观察回灌，允许改写重试一次。

与旧版的区别：旧图由意图 argmax 决定唯一分支，一条消息带多个诉求只能答一半，
且"调什么"完全写在人工 if-else 里；现在选择权交给 function calling。
⚠ 已实测生效的闸门（知识库阈值拒答、导购 REC_MIN_SCORE=0.47 与域外拒答、LIKE 优先于域判定）
一律留在 app/agent/executors.py 内部，重构只改"谁决定调用什么"，不改"调用之后怎么把关"。

旧的业务节点（faq_retrieve/order_query/recommend_query…）已删除：主链路只走 agent，
同一套闸门留两份实现必然漂开，取数一律经 app/agent/executors.py 的唯一实现。
"""
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from app.agent.executors import ToolContext
from app.agent.nodes import (
    make_agent_node,
    make_generate_node,
    make_human_fallback_node,
    make_intent_node,
    make_smalltalk_node,
    route_by_intent,
)
from app.agent.state import AgentState

# 哨兵：区分"未传"（默认 MemorySaver）与"显式传 None"（关闭多轮记忆，单测用）
_DEFAULT_CHECKPOINTER = object()


def build_graph(llm, retriever=None, java_api_module=None, checkpointer=_DEFAULT_CHECKPOINTER,
                product_store=None, personalizer=None):
    """构建并编译客服智能体图。所有依赖均可传真实实现或测试 stub。

    checkpointer: 默认 MemorySaver（进程内多轮记忆）；单测传 None 关闭。
    product_store: 商品向量召回索引（导购一级召回源）；None 时导购只剩 LIKE/热销通道。
    personalizer: 个性化重排（登录用户偏好加权）；None 时纯语义召回序。
    """
    ctx = ToolContext(retriever=retriever, product_store=product_store,
                      personalizer=personalizer, java_api_module=java_api_module)

    builder = StateGraph(AgentState)
    builder.add_node("intent", make_intent_node())
    builder.add_node("agent", make_agent_node(llm, ctx))
    builder.add_node("generate", make_generate_node(llm))
    builder.add_node("smalltalk", make_smalltalk_node(llm))
    builder.add_node("human_fallback", make_human_fallback_node())

    builder.add_edge(START, "intent")
    builder.add_conditional_edges("intent", route_by_intent, {
        "agent": "agent",
        "smalltalk": "smalltalk",
        "human_fallback": "human_fallback",
    })
    builder.add_edge("agent", "generate")
    builder.add_edge("generate", END)
    builder.add_edge("smalltalk", END)
    builder.add_edge("human_fallback", END)
    if checkpointer is _DEFAULT_CHECKPOINTER:
        checkpointer = MemorySaver()
    return builder.compile(checkpointer=checkpointer)
