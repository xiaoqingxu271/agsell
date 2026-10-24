"""LangGraph 节点（智能体版）

图结构：intent（仅高置信规则前置，零 LLM）→ 条件路由：
  smalltalk      → smalltalk → END
  human_fallback → human_fallback → END
  agent          → agent（bind_tools 有界工具编排）→ generate → END

agent 节点把"调哪个工具"交给大模型 function calling：一轮可并行多个诉求、空结果回灌
观察后允许改写重试，步数与调用数由 settings.AGENT_MAX_STEPS / AGENT_MAX_TOOL_CALLS 封顶。
把关逻辑一律留在 app/agent/executors.py（量化实验实测出来的闸门，重构不动）。

旧业务节点（faq_retrieve/order_query/logistics_query/after_sales_query/recommend_query）
继续保留注册：既有单测直接构造它们验证闸门，功能上与执行器等价。
依赖注入：llm / retriever / java_api / product_store / personalizer，均为可测试接口。
"""
import json
import logging

from langchain_core.messages import (
    AIMessage,
    AnyMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
    trim_messages,
)

from app.agent.executors import build_contextual_query, load_product_context, run_tool
from app.agent.intent import _extract_order_no, _hard_rule, classify_full, count_demands, looks_multi_demand
from app.agent.tool_defs import TOOL_SCHEMAS
from app.config import settings
from app.prompts import (
    AGENT_PLAN_BROWSING_NOTE,
    build_agent_answer_prompt,
    build_agent_plan_prompt,
    build_degraded_reply,
    build_human_fallback_reply,
    build_kb_refusal_reply,
    build_product_qa_prompt,
    build_rec_refusal_reply,
    build_smalltalk_prompt,
)

logger = logging.getLogger("agsell.ai.nodes")

# 规划器看的对话历史上限（条）：它的任务只是补全指代与选工具，不需要整段会话
_PLANNER_HISTORY_TURNS = 6


# ---------- 规则前置：零成本短路（转人工/纯寒暄），其余交给智能体 ----------
def make_intent_node():
    """只做高置信、零成本、不需要取数的判断：human 与小寒暄在此定案，直接少一次 LLM 往返。

    实测（实验二分特征交叉）：规则通道在 hold-out 全集只有 37.6%、无词面线索子集 23.4%，
    所以它不能当语义分类器；但 human 强诉求词与纯寒暄这两类是它真正擅长的位置
    （高置信 + 毫秒级 + 省额度）。业务意图一律不再由词表决定，改由 agent 的工具选择决定。
    """

    def intent_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)

        # 工具产物与观察都是「本轮临时数据」，但 state 经 checkpointer 按 thread_id 跨轮持久化：
        # 不在此重置，本轮会读到上一轮的订单/推荐 JSON 当上下文，既答非所问又会串隐私。
        return {
            "intent": _hard_rule(question) or "",
            "order_no": _extract_order_no(question),
            "observations": [],
            "rec_items": [],
            "product_context": "",
            "product_item": None,
            "human_fallback": False,
        }

    return intent_node


# ---------- 路由 ----------
def route_by_intent(state: dict) -> str:
    """规则前置命中高置信短路意图时直达对应节点；其余一律交给智能体规划工具。

    旧版在此按 argmax 单标签把消息送进某一个工具节点，一条消息带多个诉求时只能答一半；
    现在只有 human/smalltalk 这两类"零成本、高置信、无需取数"的判断继续走短路，
    其它一律进 agent，由大模型决定调用哪些工具（可并行多个）。
    """
    intent = state.get("intent") or ""
    if intent == "smalltalk":
        return "smalltalk"
    if intent == "human":
        return "human_fallback"
    return "agent"


# ---------- 智能体：有界工具编排（bind_tools + 去重 + 反思重试 + 故障降级）----------
# 意图标签 → 降级时要调用的工具。LLM 规划失败时用，保证"最坏情况仍有一次取证"而不是空手回答。
_INTENT_TO_TOOL = {
    "faq": ("kb_search", lambda s, q: {"question": q}),
    "recommend": ("product_search", lambda s, q: {"query": q}),
    "order": ("order_query", lambda s, q: {"orderNo": s.get("order_no") or ""}),
    "logistics": ("logistics_query", lambda s, q: {"orderNo": s.get("order_no") or ""}),
    "after_sales": ("after_sales_query", lambda s, q: {}),
    "human": ("escalate_human", lambda s, q: {"reason": "规则兜底判定为需人工"}),
    # smalltalk 不在表里：寒暄不需要取证，_legacy_plan 直接返回空观察，
    # 由生成节点走"本轮无资料"的引导式分支（拿 kb_search 查问候语是纯浪费）
}


def _tool_feedback(obs) -> str:
    """回灌给规划器的观察摘要：只给"够不够用"的信号，不把大段资料再喂一遍。

    规划器不需要读资料原文（作答由 generate 节点看完整观察），少喂一坨文本既省 token
    也更有利于收敛——实测把完整结果原样回灌时，模型有概率把同样的调用再来一遍。
    """
    out: dict = {"tool": obs.tool, "status": obs.status}
    if obs.items:
        out["count"] = len(obs.items)
        out["names"] = [str(i.get("name", ""))[:20] for i in obs.items[:3]]
    elif obs.payload:
        out["chars"] = len(obs.payload)
    if obs.hint:
        out["hint"] = obs.hint
    elif obs.status in ("empty", "error", "need_login"):
        out["hint"] = "本次没有取到可用资料：可换一个检索表述或品类词重试一次，否则就此如实说明"
    return json.dumps(out, ensure_ascii=False)


def _resolve_planner(llm, schemas):
    """能力协商：上游支持 function calling 才走智能体，否则退回"规则分类 + 单工具"。

    不是偷懒的兼容层——Agnes 这类免费网关今天支持、明天换型号就可能不支持，
    此时整条客服链不能报错，只能降级成改造前的行为（慢一点、一次只答一个诉求）。
    降级会打 WARNING，避免"以为在用智能体、其实一直在跑降级路径"这种静默退化。
    """
    try:
        return llm.bind_tools(schemas)
    except (NotImplementedError, AttributeError, TypeError):
        logger.warning("上游模型不支持 function calling，智能体降级为规则分类 + 单工具路由")
        return None
    except Exception:  # noqa: BLE001
        logger.warning("bind_tools 初始化失败，智能体降级为规则分类 + 单工具路由", exc_info=True)
        return None


def _legacy_plan(state: dict, question: str, ctx, run_tool, llm) -> list:
    """降级/兜底路径：一次 LLM 调用做意图分类（含导购偏好），只执行匹配的那一个工具。

    等价于改造前的「intent → 单分支 → 单工具」，保证上游模型不支持 function calling、
    或规划调用直接抛异常时功能不回退（慢一点、一次只答一个诉求，但仍给出有依据的答案）。
    classify_full 内部已含「高置信规则前置 → LLM → 关键词兜底」三级，永不自异常。
    """
    d = classify_full(question, llm)
    intent = d.intent if d.intent in _INTENT_TO_TOOL else "faq"
    if d.intent == "smalltalk":
        return []
    name, arg_fn = _INTENT_TO_TOOL[intent]
    args = arg_fn(state, question)
    if name == "product_search":
        # 偏好随那次分类调用一次产出，这里只是换成执行器认识的参数形态；
        # 召回串用历史感知改写（"便宜点的"继承上一轮"水果"），分类仍看最后一条
        args = {"query": build_contextual_query(state), "keyword": d.keyword,
                "maxPrice": d.max_price, "inDomain": d.in_domain}
    return [run_tool(ctx, state, name, args)]


def make_agent_node(llm, ctx, tool_schemas=None):
    """规划 → 执行 → （必要时）反思重试，最多 settings.AGENT_MAX_STEPS 步。

    只负责取证，不产出面向用户的文字：实测模型拿到工具结果后可能不收敛（再发一遍同样的
    调用），所以"结束"由步数上限决定，最终回复交给 generate 节点基于观察组织。
    """
    schemas = tool_schemas or TOOL_SCHEMAS
    planner = _resolve_planner(llm, schemas)

    def agent_node(state: dict) -> dict:
        last = state["messages"][-1]
        question = last.content if isinstance(last, HumanMessage) else str(last.content)

        # 商详页上下文整轮只取一次，回填进 state 副本供各执行器复用（避免重复回调 Java）
        facts, item = load_product_context(state, ctx.api)
        state = {**state, "product_context": facts, "product_item": item}

        if planner is None:  # 降级：规则分类 + 单工具
            obs = _legacy_plan(state, question, ctx, run_tool, llm)
            return _collapse_observations(obs, facts=facts, item=item)

        # 历史只给最近几条：规划器的任务是"决定调哪个工具"，不需要整段对话
        history = [m for m in list(state["messages"])[:-1]][-_PLANNER_HISTORY_TURNS:]
        plan_system = build_agent_plan_prompt(settings.PLATFORM_NAME, settings.AGENT_MAX_STEPS)
        if facts:
            # 不告诉它"这个"是什么，模型会因为指代补不出来而干脆不调工具（E2E 实测：反问用户"您指哪款"）
            plan_system = AGENT_PLAN_BROWSING_NOTE.format(facts=facts) + plan_system
        messages = [SystemMessage(content=plan_system)]
        messages += history
        messages.append(HumanMessage(content=question))

        obs: list = []
        seen: set = set()
        calls = 0
        # 多诉求覆盖保证：连接词/句读确定性计数，诉求多于已执行工具数时跳过提前退出，
        # 让第二步规划变成"补齐核对轮"（实测 3 条探针 1 条漏答的修复，单诉求零代价）
        demands = count_demands(question) if looks_multi_demand(question) else 1
        for _step in range(settings.AGENT_MAX_STEPS):
            if calls >= settings.AGENT_MAX_TOOL_CALLS:
                break
            if _step > 0 and demands > 1:
                # 补齐轮提示：告诉模型对照逐个诉求核对，缺哪个补哪个
                messages.append(SystemMessage(content=(
                    f"【诉求核对】用户这条消息包含约 {demands} 个诉求，目前已取证 {calls} 次。"
                    "请对照用户消息逐个诉求核对：还缺哪个诉求就补调对应工具；"
                    "若已全部覆盖，输出空内容结束。")))
            try:
                resp = planner.invoke(messages)
            except Exception:  # noqa: BLE001 规划失败也要给用户答案：降级到规则选定的单个工具
                logger.warning("工具规划 LLM 调用失败，降级规则路由", exc_info=True)
                obs = _legacy_plan(state, question, ctx, run_tool, llm)
                break
            tool_calls = list(getattr(resp, "tool_calls", None) or [])
            if not tool_calls:
                break  # 模型认为资料已够，或直接可以作答
            messages.append(resp)
            step_obs: list = []
            for tc in tool_calls:
                if calls >= settings.AGENT_MAX_TOOL_CALLS:
                    logger.warning("单轮工具调用数达上限 %s，截断剩余调用", settings.AGENT_MAX_TOOL_CALLS)
                    break
                name = tc.get("name") or ""
                args = tc.get("args") or {}
                try:
                    key = (name, json.dumps(args, ensure_ascii=False, sort_keys=True))
                except Exception:  # noqa: BLE001 参数不可序列化时退化为按工具名去重
                    key = (name, str(args))
                if key in seen:
                    # 模型原地打转：不再重复执行，只回一条"已查过"的观察让它继续往下走
                    logger.info("模型重复调用同样工具与参数：%s（跳过执行）", name)
                    messages.append(ToolMessage(content=json.dumps(
                        {"tool": name, "status": "already_called", "hint": "该查询已执行过，请勿重复调用"},
                        ensure_ascii=False), tool_call_id=tc.get("id") or f"{name}_dup", name=name))
                    # 同一步里重复 = 模型已经不会往前走了，继续执行/继续规划只会白烧额度
                    break
                o = run_tool(ctx, state, name, args)
                obs.append(o)
                step_obs.append(o)
                calls += 1
                seen.add(key)
                messages.append(ToolMessage(content=_tool_feedback(o),
                                            tool_call_id=tc.get("id") or f"{name}_{calls}",
                                            name=name))

            # 反思边只在"缺证据"时才走：这一步取到的资料全部可用，或已经落到确定性出口
            # （域外拒答、未登录引导——再问一次模型也改变不了），就不要再花一次规划：
            # 实测网关单次规划 6~15 s，白跑一轮等于首 token 延迟翻倍。
            # 只有 empty/error 才留一次改写重试（模型可换检索表述或换品类词）。
            # 例外：多诉求消息若取证次数 < 诉求条数，跳过提前退出走补齐核对轮——
            # 实测模型自觉覆盖不可靠（3 条探针 1 条漏答），这里是编排层的硬保证。
            if step_obs and all(o.status in ("ok", "refused", "need_login") for o in step_obs):
                if not (demands > 1 and calls < demands):
                    break

        return _collapse_observations(obs, facts=facts, item=item)

    return agent_node


def _collapse_observations(obs: list, facts: str = "", item: dict | None = None) -> dict:
    """工具观察 → 图状态：卡片数据、商品上下文、是否转人工、知识库引用。

    observations 是唯一的"本轮取到了什么"表示——不再另存一份 tool_type/tool_result 视图：
    两处表示同一事实必然漂开，而多工具一轮取证本来就没有"单一 tool_type"可言。
    """
    items: list = []
    kinds: list[str] = []
    references: list[dict] = []
    seen_ref_ids: set = set()
    for o in obs:
        if o.items:
            items = items + o.items
        if o.kind == "product_qa" and not facts:
            facts = o.payload
        # 知识库引用去重合并（多工具多轮只留首次出现的条目），整轮上限 4 条
        for ref in o.references or []:
            if ref.get("id") not in seen_ref_ids and len(references) < 4:
                seen_ref_ids.add(ref.get("id"))
                references.append(ref)
        kinds.append(o.kind or o.tool)
    return {
        "observations": [o.as_dict() for o in obs],
        "rec_items": items,
        "references": references,
        # 商品上下文：优先用本轮 agent 预取的事实（整轮只回调 Java 一次）
        "product_context": facts or "",
        "product_item": item,
        "human_fallback": any(k == "human" for k in kinds),
    }



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


# ---------- 生成：本轮取到的资料 → 回复（唯一的作答出口）----------
def make_generate_node(llm):
    """只依据本轮工具观察作答；一条观察都没取到时不硬答，也没有"资料缺失却照常生成"的口子。

    五类确定性出口，顺序即优先级（都是量化实验实测出来的闸门）：
    转人工话术 → 域外拒答（不经 LLM）→ 未登录引导 → 商品事实问答 → 取数故障友好降级
    → 知识库未覆盖拒答；只有真的取到 status=ok 的资料时才交给 LLM 组织回复。
    """
    platform = settings.PLATFORM_NAME
    phone = settings.SERVICE_PHONE

    def generate_node(state: dict) -> dict:
        obs = state.get("observations") or []
        product_context = state.get("product_context") or ""

        # 规划器一个工具都没调：多半是礼貌回应或纯追问，本轮不需要事实取证。
        # 此时不能按"知识库没覆盖"拒答（根本没查过，拒得不成立），改为引导式回复——
        # 该提示词明确要求不编造业务信息，具体业务问题让用户直接提问
        if not obs:
            return _invoke_and_finish(llm, build_smalltalk_prompt(platform), state)

        # 只有 status=ok 的观察才算"取到资料"：error 的 payload 一律为空（异常细节只在日志），
        # empty/refused/need_login 也不得进上下文——否则内网地址、集合名这类字样有概率被复述给用户
        usable = [o for o in obs if (o.get("payload") or o.get("items")) and o.get("status") == "ok"]
        statuses = {o.get("status") for o in obs}
        # 转人工本身没有资料，但它是必须告知用户的事实（"已为您转人工"），要一起进上下文
        human_obs = [o for o in obs if o.get("kind") == "human"]

        if not usable and not human_obs:
            if "human" in {o.get("kind") for o in obs}:
                # 智能体判定必须人工跟进：直接给转人工话术（与规则前置同一出口），
                # 不能让"本轮没取到资料"被误判成"知识库未覆盖"
                return {
                    "messages": [AIMessage(content=build_human_fallback_reply(phone))],
                    "suggestions": list(_SUGGESTIONS["human"]),
                }
            if "refused" in statuses:
                # 域外导购：与知识库闸门同一套原则——没有可信候选时不经 LLM、不硬凑商品
                return {
                    "messages": [AIMessage(content=build_rec_refusal_reply(
                        platform=platform, service_phone=phone))],
                    "suggestions": list(_SUGGESTIONS["recommend"]),
                }
            login = next((o for o in obs if o.get("status") == "need_login"), None)
            if login:
                # 未登录：直接输出引导话术，不经 LLM 复述（省一次调用，也不会编造订单）
                return {
                    "messages": [AIMessage(content=login.get("payload") or build_degraded_reply(phone))],
                    "suggestions": list(_SUGGESTIONS["order"]),
                }
            if product_context:
                # 检索为空但用户手上正看着一件商品：答案在商品事实里，不该按"知识库没覆盖"拒答
                return _invoke_and_finish(llm, build_product_qa_prompt(
                    facts=product_context, platform=platform, service_phone=phone), state)
            if "error" in statuses:
                # 取数依赖故障（Java/向量库/检索器不可用）：友好降级话术，绝不裸露系统细节
                return {
                    "messages": [AIMessage(content=build_degraded_reply(phone))],
                    "suggestions": _suggestions_for(state),
                }
            return {
                "messages": [AIMessage(content=build_kb_refusal_reply(phone))],
                "suggestions": _suggestions_for(state),
            }

        # 只取到"用户正在浏览的这件商品"：改用商品问答提示词——它明确要求把"这个/这款"
        # 当指代对象，通用作答提示词没这条，模型有概率反问"您指的是哪一件"
        if usable and all(o.get("kind") == "product_qa" for o in usable):
            return _invoke_and_finish(llm, build_product_qa_prompt(
                facts=usable[0].get("payload") or product_context,
                platform=platform, service_phone=phone), state)

        # 多意图并行取证时这里有多条资料，一次回答全部诉求（旧的单分支路由做不到）
        return _invoke_and_finish(llm, build_agent_answer_prompt(
            context_text=json.dumps(usable + human_obs, ensure_ascii=False),
            platform=platform, service_phone=phone,
            product_context=product_context), state)

    return generate_node


def _invoke_and_finish(llm, system_prompt: str, state: dict) -> dict:
    """统一的"调用 + 降级 + 小贴士"出口：LLM 故障一律回降级话术，绝不白屏也不抛异常。"""
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
        # 阶段四：按上一轮意图/本轮取到的资料给下一轮建议问题（小贴士）
        "suggestions": _suggestions_for(state),
    }


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
    obs = state.get("observations") or []
    if obs:
        # 智能体路径没有单一 intent，按本轮实际取到的资料类型给建议
        kinds = {o.get("kind") for o in obs}
        if "recommend" in kinds or "product_qa" in kinds:
            return list(_SUGGESTIONS["recommend"])
        if "order" in kinds:
            return list(_SUGGESTIONS["order"])
        if "logistics" in kinds:
            return list(_SUGGESTIONS["logistics"])
        if "after_sales" in kinds:
            return list(_SUGGESTIONS["after_sales"])
        if "human" in kinds:
            return list(_SUGGESTIONS["human"])
        return list(_SUGGESTIONS["faq"])
    intent = state.get("intent") or "faq"
    if intent == "order":
        order_no = state.get("order_no")
        if order_no:
            return [f"订单 {order_no} 的物流到哪了？", "怎么申请退款？", "我的售后处理得怎么样？"]
        return list(_SUGGESTIONS["order"])
    return list(_SUGGESTIONS.get(intent, _SUGGESTIONS["faq"]))
