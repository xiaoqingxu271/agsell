"""LangGraph 节点（阶段三；阶段六新增智能导购）

图结构：intent → 条件路由：
  faq        → faq_retrieve → generate
  order      → order_query → generate
  logistics  → logistics_query → generate
  after_sales→ after_sales_query → generate
  recommend  → recommend_query → generate（导购：偏好抽取 → 商品搜索 → 推荐话术）
  smalltalk  → smalltalk → END
  human      → human_fallback → END

依赖注入：llm / retriever / java_api，均为可测试接口。
"""
import json
import re

from langchain_core.messages import (
    AIMessage,
    AnyMessage,
    HumanMessage,
    SystemMessage,
    trim_messages,
)

from app.agent.intent import classify_intent
from app.config import settings
from app.prompts import (
    build_degraded_reply,
    build_human_fallback_reply,
    build_recommend_extract_prompt,
    build_recommend_system_prompt,
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
    if intent == "recommend":
        return "recommend_query"
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


# ---------- 智能导购（阶段六）----------
def _extract_preferences(question: str, llm) -> tuple[str | None, float | None]:
    """从用户消息抽取（品类关键词, 价格上限）。LLM 失败返回 (None, None)，
    导购节点随即降级为"按销量推荐热销商品"，路由与回复不中断。"""
    try:
        response = llm.invoke([SystemMessage(content=build_recommend_extract_prompt(question))])
        text = (response.content or "").strip()
        text = re.sub(r"^```(?:json)?|```$", "", text, flags=re.MULTILINE).strip()
        data = json.loads(text)
        keyword = data.get("keyword") or None
        max_price = data.get("maxPrice")
        max_price = float(max_price) if isinstance(max_price, (int, float)) else None
        return (str(keyword).strip() or None) if keyword else None, max_price
    except Exception:
        return None, None


def make_recommend_node(java_api_module=None, llm=None):
    api = java_api_module or java_api

    def recommend_query_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)
        keyword, max_price = _extract_preferences(question, llm) if llm else (None, None)
        try:
            products = api.search_products(keyword=keyword, max_price=max_price)
            if not products and keyword:
                # 品类词 LIKE 无命中（用户说"水果"，商品名是"赣南脐橙"）→ 降级热销榜，
                # 并在上下文中注明原因，由生成节点如实引导
                products = api.search_products(max_price=max_price)
                if products:
                    note = f"（未找到与「{keyword}」直接匹配的商品，以下为热销候选，请如实向用户说明并引导其换个说法）"
                    return {"tool_result": note + "\n" + api.format_payload(products), "tool_type": "recommend"}
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询商品失败：{e}", "tool_type": "error"}
        if not products:
            return {"tool_result": "未找到符合偏好的在售商品", "tool_type": "recommend"}
        return {"tool_result": api.format_payload(products), "tool_type": "recommend"}

    return recommend_query_node


# ---------- 多轮历史窗口截断（阶段七）----------
def _message_char_len(messages) -> int:
    """字符数计数器，兼容 trim_messages 的两种调用约定：传单条消息，或传整个消息列表。

    中文按字符近似计 token（避免 'approximate' 按空白切词把整句中文算作 1 个 token）。
    """
    # trim_messages 可能直接传入消息列表（list 计数器分支）
    if isinstance(messages, (list, tuple)):
        return sum(_message_char_len(m) for m in messages)
    content = getattr(messages, "content", "")
    if isinstance(content, str):
        return len(content)
    if isinstance(content, list):  # 多模态片段 [{'type': 'text', 'text': ...}]
        return sum(len(part.get("text", "")) for part in content if isinstance(part, dict))
    return 0


def _build_messages(system_prompt: str, history: list, max_chars: int | None = None) -> list:
    """组装发给 LLM 的消息：system 常驻 + 历史按字符预算保留最近对话。

    - max_chars 默认取 settings.HISTORY_MAX_CHARS，<=0 表示不截断；
    - strategy="last" 保留最近对话；include_system=True 始终保留系统提示词；
    - start_on="human" 保证截断后从一条完整 HumanMessage 开始，避免半截轮次。
    """
    budget = settings.HISTORY_MAX_CHARS if max_chars is None else max_chars
    messages: list[AnyMessage] = [SystemMessage(content=system_prompt), *list(history)]
    if budget and budget > 0:
        messages = trim_messages(
            messages,
            max_tokens=budget,
            token_counter=_message_char_len,
            strategy="last",
            include_system=True,
            start_on="human",
            allow_partial=False,
        )
    return messages


# ---------- 生成 ----------
def make_generate_node(llm):
    def generate_node(state: dict) -> dict:
        if state.get("tool_result"):
            if state.get("tool_type") == "recommend":
                system_prompt = build_recommend_system_prompt(
                    context_text=state["tool_result"],
                    platform=settings.PLATFORM_NAME,
                    service_phone=settings.SERVICE_PHONE,
                )
            else:
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
        messages = _build_messages(system_prompt, state["messages"])
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
        messages = _build_messages(
            build_smalltalk_prompt(settings.PLATFORM_NAME), state["messages"]
        )
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
    "recommend": ["有什么水果推荐？", "50 元以内有什么好物？", "销量最高的商品是哪些？"],
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
