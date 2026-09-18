"""LangGraph 节点（阶段三）

图结构：intent → 条件路由：
  faq        → faq_retrieve → generate
  order      → order_query → generate
  logistics  → logistics_query → generate
  after_sales→ after_sales_query → generate
  smalltalk  → smalltalk → END
  human      → human_fallback → END

依赖注入：llm / retriever / java_api，均为可测试接口。
"""
from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage

from app.agent.intent import classify_intent
from app.config import settings
from app.prompts import (
    build_degraded_reply,
    build_human_fallback_reply,
    build_smalltalk_prompt,
    build_system_prompt,
    build_tool_system_prompt,
    format_context,
)
from app.tools import java_api


# ---------- 意图识别 ----------
def make_intent_node(llm):
    def intent_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)
        intent, order_no = classify_intent(question, llm)
        return {"intent": intent, "order_no": order_no}

    return intent_node


# ---------- 路由 ----------
def route_by_intent(state: dict) -> str:
    intent = state.get("intent") or "faq"
    if intent == "order":
        return "order_query"
    if intent == "logistics":
        return "logistics_query"
    if intent == "after_sales":
        return "after_sales_query"
    if intent == "smalltalk":
        return "smalltalk"
    if intent == "human":
        return "human_fallback"
    return "faq_retrieve"  # faq 及默认


# ---------- FAQ 检索 ----------
def make_faq_retrieve_node(retriever):
    def faq_retrieve_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)
        entries = retriever.retrieve(question)
        return {"faq_context": entries}

    return faq_retrieve_node


# ---------- 业务工具（回调 Java）----------
def _user_id_or_error(state: dict) -> str | None:
    user_id = state.get("userId")
    if user_id:
        return None
    return "暂时无法确认您的登录身份，请到小程序登录后咨询，或拨打客服电话 " + settings.SERVICE_PHONE + " 处理。"


def make_order_query_node(java_api_module=None):
    api = java_api_module or java_api

    def order_query_node(state: dict) -> dict:
        err = _user_id_or_error(state)
        if err:
            return {"tool_result": err, "tool_type": "error"}
        order_no = state.get("order_no")
        try:
            if order_no:
                data = api.query_order_detail(state["userId"], order_no)
                return {"tool_result": api.format_payload(data), "tool_type": "order"}
            data = api.query_orders(state["userId"])
            return {"tool_result": api.format_payload(data), "tool_type": "order"}
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询订单失败：{e}", "tool_type": "error"}

    return order_query_node


def make_logistics_query_node(java_api_module=None):
    api = java_api_module or java_api

    def logistics_query_node(state: dict) -> dict:
        err = _user_id_or_error(state)
        if err:
            return {"tool_result": err, "tool_type": "error"}
        order_no = state.get("order_no")
        try:
            # 未指定订单号时取最近一笔订单查物流
            if not order_no:
                orders = api.query_orders(state["userId"])
                if not orders:
                    return {"tool_result": "该用户暂无订单", "tool_type": "logistics"}
                order_no = orders[0]["orderNo"]
            data = api.query_logistics(state["userId"], order_no)
            return {"tool_result": api.format_payload(data), "tool_type": "logistics"}
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询物流失败：{e}", "tool_type": "error"}

    return logistics_query_node


def make_after_sales_query_node(java_api_module=None):
    api = java_api_module or java_api

    def after_sales_query_node(state: dict) -> dict:
        err = _user_id_or_error(state)
        if err:
            return {"tool_result": err, "tool_type": "error"}
        try:
            data = api.query_after_sales(state["userId"])
            return {"tool_result": api.format_payload(data), "tool_type": "after_sales"}
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询售后失败：{e}", "tool_type": "error"}

    return after_sales_query_node


# ---------- 生成 ----------
def make_generate_node(llm):
    def generate_node(state: dict) -> dict:
        if state.get("tool_result"):
            system_prompt = build_tool_system_prompt(
                context_text=state["tool_result"],
                platform=settings.PLATFORM_NAME,
                service_phone=settings.SERVICE_PHONE,
            )
        else:
            context = state.get("faq_context") or []
            system_prompt = build_system_prompt(
                context_text=format_context(context),
                platform=settings.PLATFORM_NAME,
                service_phone=settings.SERVICE_PHONE,
            )
        messages: list[AnyMessage] = [SystemMessage(content=system_prompt)]
        messages.extend(state["messages"])
        try:
            response = llm.invoke(messages)
        except Exception as e:  # 阶段五：Agnes 限流/超时/断网 → 降级话术，不白屏
            response = AIMessage(
                content=build_degraded_reply(settings.SERVICE_PHONE, error=str(e))
            )
        return {
            "messages": [response],
            # 阶段四：按上一轮意图给下一轮建议问题（小贴士）
            "suggestions": _suggestions_for(state),
        }

    return generate_node


# ---------- 闲聊 ----------
def make_smalltalk_node(llm):
    def smalltalk_node(state: dict) -> dict:
        messages: list[AnyMessage] = [
            SystemMessage(content=build_smalltalk_prompt(settings.PLATFORM_NAME))
        ]
        messages.extend(state["messages"])
        try:
            response = llm.invoke(messages)
        except Exception as e:  # 阶段五：降级话术
            response = AIMessage(
                content=build_degraded_reply(settings.SERVICE_PHONE, error=str(e))
            )
        return {"messages": [response], "suggestions": _SUGGESTIONS["smalltalk"]}

    return smalltalk_node


# ---------- 转人工兜底 ----------
def make_human_fallback_node():
    def human_fallback_node(state: dict) -> dict:
        reply = build_human_fallback_reply(settings.SERVICE_PHONE)
        return {
            "messages": [AIMessage(content=reply)],
            "human_fallback": True,
            "suggestions": _SUGGESTIONS["human"],
        }

    return human_fallback_node


# ---------- 下一轮建议问题（小贴士）----------
_SUGGESTIONS = {
    "faq": ["怎么申请退款？", "运费怎么算的？", "我的订单现在是什么状态？"],
    "order": ["我的订单物流到哪了？", "怎么申请退款？", "我的售后处理得怎么样？"],
    "logistics": ["我的订单现在是什么状态？", "怎么申请退款？", "我申请过退款吗？"],
    "after_sales": ["我的订单现在是什么状态？", "退款多久到账？", "客服电话是多少？"],
    "smalltalk": ["你们卖什么水果？", "怎么下单购买？", "运费怎么算的？"],
    "human": ["客服电话是多少？", "我的订单现在是什么状态？", "怎么申请退款？"],
}


def _suggestions_for(state: dict) -> list[str]:
    intent = state.get("intent") or "faq"
    if intent == "order":
        order_no = state.get("order_no")
        if order_no:
            return [f"订单 {order_no} 的物流到哪了？", "怎么申请退款？", "我的售后处理得怎么样？"]
        return list(_SUGGESTIONS["order"])
    return list(_SUGGESTIONS.get(intent, _SUGGESTIONS["faq"]))
