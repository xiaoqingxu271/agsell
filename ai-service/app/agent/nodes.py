"""LangGraph 节点（阶段三 + 阶段六导购；本轮：偏好随意图一次产出 + 导购闸门与拒答出口）

图结构：intent → 条件路由：
  faq        → faq_retrieve → generate
  order      → order_query → generate
  logistics  → logistics_query → generate
  after_sales→ after_sales_query → generate
  recommend  → recommend_query → generate
               （导购四级：商品向量语义召回 ≥REC_MIN_SCORE → 品类词 LIKE →
                 明确域外则确定性拒答 → 否则热销榜兜底）
  smalltalk  → smalltalk → END
  human      → human_fallback → END

依赖注入：llm / retriever / java_api / product_store，均为可测试接口。
"""
import json
import logging
import re

from langchain_core.messages import (
    AIMessage,
    AnyMessage,
    HumanMessage,
    SystemMessage,
    trim_messages,
)

from app.agent.intent import classify_full
from app.config import settings
from app.prompts import (
    build_degraded_reply,
    build_human_fallback_reply,
    build_kb_refusal_reply,
    build_product_qa_prompt,
    build_rec_refusal_reply,
    build_recommend_extract_prompt,
    build_recommend_system_prompt,
    build_smalltalk_prompt,
    build_system_prompt,
    build_tool_system_prompt,
    format_context,
)
from app.tools import java_api

logger = logging.getLogger("agsell.ai.nodes")


# ---------- 意图识别（同一次调用产出路由 + 导购偏好）----------
def make_intent_node(llm):
    def intent_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)
        d = classify_full(question, llm)
        # 工具与偏好产物都是「本轮临时数据」，但 state 经 checkpointer 按 thread_id 跨轮持久化：
        # 不在此重置，本轮走 FAQ 分支时 generate 会先读到上一轮的订单/推荐 JSON 当
        # system prompt，既答非所问又会把上一轮隐私复述进无关话题。
        return {
            "intent": d.intent,
            "order_no": d.order_no,
            "tool_result": "",
            "tool_type": "",
            # 导购偏好随意图产出：省掉导购分支的一次串行 LLM 调用（实测首 token 19.2 s 的主因）
            "rec_keyword": d.keyword,
            "rec_max_price": d.max_price,
            "rec_in_domain": d.in_domain,
            # 商品上下文与卡片候选同样逐轮重置：上一轮咨询的商品不能渗进本轮无关问题
            "product_context": "",
            "product_item": None,
            "rec_items": [],
        }

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


# ---------- 商详页咨询上下文（"这个"的指代对象）----------
def load_product_context(state: dict, api_module) -> tuple[str, dict | None]:
    """按 state.productId 取该在售商品的事实。

    没有 productId、商品已下架、或 Java 侧不可用时一律返回 ("", None)——
    聊天链路绝不因为"取上下文失败"而报错给用户，退回常规问答即可。
    """
    pid = state.get("productId")
    if not pid:
        return "", None
    try:
        item = api_module.fetch_product(int(pid))
    except Exception as e:
        logger.warning("商品上下文获取失败（忽略，退回常规问答）：productId=%s, %s", pid, e)
        return "", None
    if not item:
        return "", None
    return java_api.format_product_facts(item), item


# ---------- FAQ 检索 ----------
def make_faq_retrieve_node(retriever, java_api_module=None):
    api = java_api_module or java_api

    def faq_retrieve_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)
        entries = retriever.retrieve(question)
        # 商品上下文与知识库检索无关，但必须在本轮取好：
        # generate 靠它判断"检索为空"是真的没资料，还是用户在问手里这件商品
        facts, item = load_product_context(state, api)
        return {"faq_context": entries, "product_context": facts, "product_item": item}

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


# ---------- 智能导购（语义召回 → 品类 LIKE → 域外拒答 → 热销兜底）----------
def _extract_preferences(question: str, llm) -> tuple[str | None, float | None, bool | None]:
    """独立偏好抽取——**仅作节点被单独调用时的兜底**。

    生产链路里导购条件已随意图一次产出（见 make_intent_node），不再走这里；保留本函数是为了
    ① 单测直接构造 recommend 节点时仍可用，② 意图节点被跳过时不至于没有偏好。
    LLM 失败返回 (None, None, None)，语义是"没有判定"——下游按原三级降级处理，绝不据此拒答。
    """
    if llm is None:
        return None, None, None
    try:
        response = llm.invoke([SystemMessage(content=build_recommend_extract_prompt(question))])
        text = (response.content or "").strip()
        text = re.sub(r"^```(?:json)?|```$", "", text, flags=re.MULTILINE).strip()
        data = json.loads(text)
        keyword = data.get("keyword") or None
        max_price = data.get("maxPrice")
        max_price = float(max_price) if isinstance(max_price, (int, float)) else None
        in_domain = data.get("inDomain") if isinstance(data.get("inDomain"), bool) else None
        return (str(keyword).strip() or None) if keyword else None, max_price, in_domain
    except Exception:
        return None, None, None


def _reorder_by_recall(products: list[dict], recalled_ids: list[int]) -> list[dict]:
    """按向量召回顺序重排 Java 返回的商品列表（Java 侧默认销量降序，这里恢复语义相关度序）"""
    rank = {pid: i for i, pid in enumerate(recalled_ids)}
    return sorted(products, key=lambda p: rank.get(p.get("id"), len(recalled_ids)))


def make_recommend_node(java_api_module=None, llm=None, product_store=None, personalizer=None):
    api = java_api_module or java_api

    def recommend_query_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)

        # 导购偏好优先取意图节点同轮产出；state 里没有这些键（节点被单独调用）才回退独立抽取
        if "rec_in_domain" in state:
            keyword = state.get("rec_keyword")
            max_price = state.get("rec_max_price")
            in_domain = state.get("rec_in_domain")
        else:
            keyword, max_price, in_domain = _extract_preferences(question, llm)

        facts, item = load_product_context(state, api)

        def _recommend(products: list[dict], note: str = "") -> dict:
            """统一出口：文本给 LLM 组话术，结构化 rec_items 给小程序画可点击卡片。

            只给文本是不够的——图片/价格/跳转 id 进了 prompt 就丢了，用户看完推荐
            还得自己去搜一遍，导购链路在 UI 上等于断掉。
            """
            payload = api.format_payload(products)
            return {
                "tool_result": (note + "\n" + payload) if note else payload,
                "tool_type": "recommend",
                "rec_items": products,
                "product_context": facts,
                "product_item": item,
            }

        # ① 一级：商品向量语义召回（整句进索引，"适合送礼的柑橘"这类描述也能命中）。
        #    对话侧用独立闸门 REC_MIN_SCORE（0.47），不是搜索框的 0.35——搜索框空结果能退回
        #    全文检索，导购分支没有退路，阈值一松就把"手机壳"召回成"带壳毛豆"
        #    （量化实验五实测：0.35 下 10 条域外推荐请求放行 6 条）。
        #    命中 id 后仍经 Java 取详情（在售过滤/价格区间/口碑注入口径不变）；
        #    登录用户叠加个性化重排；索引为空或召回失败不阻断，逐级降级。
        if product_store is not None:
            try:
                hits = [h for h in product_store.search(question) if h["score"] >= settings.REC_MIN_SCORE]
                recalled = [h["id"] for h in hits]
                sem_by_id = {h["id"]: h["score"] for h in hits}
                if recalled:
                    products = _reorder_by_recall(
                        api.search_products(ids=recalled, max_price=max_price), recalled
                    )
                    if products:
                        if personalizer is not None:
                            products = personalizer.rerank(state.get("userId"), products, sem_by_id)
                        return _recommend(products)
            except Exception as e:
                logger.warning("商品向量召回不可用，降级关键词/热销路径：%s", e)

        # ② 二级：品类词 LIKE。命中即推荐——"库里的确有这件商品"是正面证据，
        #    优先于模型的经营域判断（宁可少拒一次，也不能把在卖的商品拒掉）。
        try:
            if keyword:
                products = api.search_products(keyword=keyword, max_price=max_price)
                if products:
                    return _recommend(products)
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询商品失败：{e}", "tool_type": "error", "rec_items": []}

        # ③ 三级：正在浏览某件商品时，"这个/这款"的默认指代就是它。
        #    既无语义命中又无品类词，最可能的诉求就是"评价/适配这件商品"，
        #    所以拿它自己当唯一候选来回答并附上卡片——而不是硬凑热销榜或直接拒答。
        if item:
            return {
                "tool_result": facts,
                "tool_type": "product_qa",
                "rec_items": [item],
                "product_context": facts,
                "product_item": item,
            }

        # ④ 四级：大模型明确判定不属于本平台经营范围 → 确定性拒答。
        #    不经 LLM、不降级热销榜（旧行为会给"推荐一双跑步鞋"硬端上金华火腿）。
        #    in_domain 为 None（没拿到判定，例如 LLM 故障）时不拒答，行为与改动前一致。
        if in_domain is False:
            return {"tool_result": "", "tool_type": "recommend_refused", "rec_items": []}

        # ⑤ 五级：热销榜兜底
        try:
            products = api.search_products(max_price=max_price)
        except java_api.JavaApiError as e:
            return {"tool_result": f"查询商品失败：{e}", "tool_type": "error", "rec_items": []}
        if not products:
            return {
                "tool_result": "未找到符合偏好的在售商品",
                "tool_type": "recommend",
                "rec_items": [],
                "product_context": facts,
                "product_item": item,
            }
        if keyword:
            # 品类词 LIKE 无命中（用户说"水果"，商品名是"赣南脐橙"）→ 降级热销榜，
            # 并在上下文中注明原因，由生成节点如实引导
            note = f"（未找到与「{keyword}」直接匹配的商品，以下为热销候选，请如实向用户说明并引导其换个说法）"
            return _recommend(products, note=note)
        return _recommend(products)

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
        # 兜底：大上下文 system（如推荐的商品 JSON）可能吃满预算，把用户消息裁光，
        # 网关会报 400 "No user query found in messages"——至少保留最近一条用户消息
        if not any(isinstance(m, HumanMessage) for m in messages):
            last_human = next((m for m in reversed(list(history)) if isinstance(m, HumanMessage)), None)
            if last_human is not None:
                messages = [*messages, last_human]
    return messages


# ---------- 生成 ----------
def make_generate_node(llm):
    def generate_node(state: dict) -> dict:
        tool_type = state.get("tool_type")
        product_context = state.get("product_context") or ""

        # 导购域外拒答：与知识库闸门同一套原则——没有可信候选时不经 LLM、不硬凑商品，
        # 直接输出确定性话术（零幻觉、省一次调用、也不会编造商品）
        if tool_type == "recommend_refused":
            return {
                "messages": [AIMessage(content=build_rec_refusal_reply(
                    platform=settings.PLATFORM_NAME, service_phone=settings.SERVICE_PHONE
                ))],
                "suggestions": list(_SUGGESTIONS["recommend"]),
            }

        # 用户在商详页问"这个"：候选就是他正在看的这件商品，按商品事实回答
        if tool_type == "product_qa":
            system_prompt = build_product_qa_prompt(
                facts=state.get("tool_result") or product_context,
                platform=settings.PLATFORM_NAME,
                service_phone=settings.SERVICE_PHONE,
            )
        elif state.get("tool_result"):
            if tool_type == "recommend":
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
            if not context:
                # 反幻觉闸门：检索零命中/全部低于阈值 → 确定性拒答，不经 LLM、零幻觉、省额度。
                # 例外：用户手里正看着一件商品（有商品事实可依据）时不该拒答——
                # "这个能放多久"在知识库里查不到，但答案确实在手上这件商品的信息里。
                if product_context:
                    system_prompt = build_product_qa_prompt(
                        facts=product_context,
                        platform=settings.PLATFORM_NAME,
                        service_phone=settings.SERVICE_PHONE,
                    )
                else:
                    return {
                        "messages": [AIMessage(content=build_kb_refusal_reply(settings.SERVICE_PHONE))],
                        "suggestions": _suggestions_for(state),
                    }
            else:
                system_prompt = build_system_prompt(
                    context_text=format_context(context),
                    platform=settings.PLATFORM_NAME,
                    service_phone=settings.SERVICE_PHONE,
                    product_context=product_context,
                )
        messages = _build_messages(system_prompt, state["messages"])
        try:
            response = llm.invoke(messages)
        except Exception as e:  # 阶段五：Agnes 限流/超时/断网 → 降级话术，不白屏
            logger.warning("生成节点 LLM 调用失败，降级话术：%s: %s", type(e).__name__, e)
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
            logger.warning("闲聊节点 LLM 调用失败，降级话术：%s: %s", type(e).__name__, e)
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
