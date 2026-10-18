"""智能体图测试：规则前置短路 + function calling 工具编排 + 降级路径（全部 stub，不依赖网络）

覆盖：
1. 路由：只有 human/smalltalk 短路，其余进 agent；
2. 编排：一轮并行多工具、空结果反思改写、(工具,参数) 去重、步数与调用数上限；
3. 出口：域外拒答/未登录/转人工/检索故障各走各的确定性话术，多诉求一次答完；
4. 能力协商：上游不支持 bind_tools 或规划调用抛异常时退回"分类 + 单工具"，功能不回退。

闸门本身（REC_MIN_SCORE、LIKE 优先、商详指代）在 tests/test_product_recall.py 针对执行器测。
"""
import json

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.agent.graph import build_graph
from app.agent.intent import classify_intent
from app.agent.nodes import route_by_intent
from app.config import settings

# ScriptedLLM 用来区分"规划调用"与"作答调用"的唯一标记
_PLAN_MARKER = "工具编排器"
_ANSWER_MARKER = "已取到的资料"
_QA_MARKER = "当前商品信息"
_SMALLTALK_MARKER = "正在和你闲聊"
_INTENT_MARKER = "意图识别器"


class StubRetriever:
    """默认命中；问题里带"查不到"时返回空，用来触发反思重试与去重分支"""

    def __init__(self):
        self.questions = []

    def retrieve(self, question, top_k=5):
        self.questions.append(question)
        if "查不到" in question:
            return []
        return [{"id": 1, "question": "怎么联系人工客服？", "answer": "客服电话 400-000-0000", "score": 0.9}]


class FakeJavaApi:
    """模拟 Java 内部接口返回"""

    def __init__(self, products=None):
        self.last_search = None
        self.products = products

    def query_orders(self, user_id, status=None):
        return [{"orderNo": "AGS202609180001", "status": 2, "statusText": "待收货", "payAmount": 39.9}]

    def query_order_detail(self, user_id, order_no):
        return {"orderNo": order_no, "status": 2, "statusText": "待收货", "logType": "顺丰", "logNo": "SF123456"}

    def query_logistics(self, user_id, order_no):
        return {"orderNo": order_no, "status": 2, "statusText": "待收货", "logType": "顺丰", "logNo": "SF123456"}

    def query_after_sales(self, user_id, status=None):
        return [{"afterSalesNo": "AS202609180001", "status": 0, "statusText": "待处理", "refundAmount": 20}]

    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        self.last_search = {"keyword": keyword, "max_price": max_price, "ids": ids}
        if self.products is not None:
            return self.products
        if keyword == "不存在的品类":
            return []
        return [
            {"id": 1, "name": "赣南脐橙", "price": 19.9, "sales": 500, "origin": "江西赣州"},
            {"id": 2, "name": "五常大米", "price": 68.0, "sales": 300, "origin": "黑龙江五常"},
        ]

    def fetch_product(self, product_id):
        return None

    def format_payload(self, data):
        return json.dumps(data, ensure_ascii=False)


class ScriptedLLM:
    """带 function calling 的替身。

    - steps：每一步要发起的 tool_calls（[[("工具", {参数}), …], …]）；步数用完后返回空
      tool_calls，等价于"模型认为资料够了"，用于验证循环会收敛；
    - answers：system prompt 片段 → 回复文本，用来断言模型看到的是哪份资料；
    - support_tools=False / plan_error=True：分别模拟"上游不支持 function calling"
      与"规划调用直接失败"两条降级路径。
    """

    def __init__(self, steps=None, answers=None, support_tools=True, plan_error=False, intent_json=None):
        self.steps = list(steps or [])
        self.answers = dict(answers or {})
        self.support_tools = support_tools
        self.plan_error = plan_error
        self.plan_inputs: list[list] = []      # 每次规划调用看到的完整消息
        self.answer_prompts: list[str] = []    # 每次作答看到的 system prompt
        self.seen_prompts: list[str] = []      # 其他（未识别模板）调用看到的 system prompt
        self.bound_schemas = None
        if intent_json:
            self.answers[_INTENT_MARKER] = intent_json
        self.answers.setdefault(_ANSWER_MARKER, "agent_generated")
        self.answers.setdefault(_QA_MARKER, "product_qa_generated")
        self.answers.setdefault(_SMALLTALK_MARKER, "smalltalk_generated")

    def bind_tools(self, schemas, **kwargs):
        if not self.support_tools:
            raise NotImplementedError("该模型不支持 tools 参数")
        self.bound_schemas = schemas
        return self

    @property
    def plan_calls(self) -> int:
        return len(self.plan_inputs)

    def invoke(self, messages):
        sys_text = next((m.content for m in messages if isinstance(m, SystemMessage)), "")
        if _PLAN_MARKER in sys_text:
            self.plan_inputs.append(list(messages))
            if self.plan_error:
                raise RuntimeError("429 Too Many Requests（规划调用模拟故障）")
            idx = len(self.plan_inputs) - 1
            if idx >= len(self.steps):
                return AIMessage(content="")  # 收敛：不再调用工具
            return AIMessage(content="", tool_calls=[
                {"name": name, "args": args, "id": f"call{idx}_{i}_{name}"}
                for i, (name, args) in enumerate(self.steps[idx])
            ])
        for marker, reply in self.answers.items():
            if marker in sys_text:
                # 只把"作答"调用记进 answer_prompts：降级路径的意图分类也是普通 invoke，
                # 混进来就没法用它证明"这一轮只花了一次 LLM"
                if marker in (_ANSWER_MARKER, _QA_MARKER, _SMALLTALK_MARKER):
                    self.answer_prompts.append(sys_text)
                return AIMessage(content=reply)
        self.seen_prompts.append(sys_text)  # 未命中任何模板：断言可以看到 no_context 的来源
        return AIMessage(content="no_context")


def _graph(llm, api=None, retriever=None, product_store=None, personalizer=None, checkpointer=None):
    return build_graph(
        llm=llm,
        retriever=retriever or StubRetriever(),
        java_api_module=api or FakeJavaApi(),
        product_store=product_store,
        personalizer=personalizer,
        checkpointer=checkpointer,
    )


def _ask(llm, message, api=None, user_id=1, **kw):
    return _graph(llm, api=api, **kw).invoke(
        {"messages": [HumanMessage(content=message)], "userId": user_id}
    )


def _tools(result) -> list[str]:
    return [o["tool"] for o in result["observations"]]


# ---------- 路由：只有零成本、高置信的判断继续短路 ----------
def test_route_by_intent_mapping():
    assert route_by_intent({"intent": "smalltalk"}) == "smalltalk"
    assert route_by_intent({"intent": "human"}) == "human_fallback"
    # 业务意图不再由词表决定：一律交给智能体选工具
    for intent in ("", "faq", "order", "logistics", "after_sales", "recommend"):
        assert route_by_intent({"intent": intent}) == "agent"
    assert route_by_intent({}) == "agent"


def test_intent_node_leaves_business_questions_unlabeled():
    """"我的订单到哪了"含"订单"词面线索，但规则层不再据此定分支——旧版靠词表分类，
    hold-out 实测只有 37.6%；现在只保留 human/纯寒暄两类高置信判断。"""
    from app.agent.nodes import make_intent_node

    node = make_intent_node()
    state = {"messages": [HumanMessage(content="我的订单到哪了")]}
    assert node(state)["intent"] == ""
    assert node({"messages": [HumanMessage(content="你好呀")]})["intent"] == "smalltalk"
    assert node({"messages": [HumanMessage(content="我要投诉")]})["intent"] == "human"


def test_smalltalk_and_human_never_call_tools():
    for message, expected in (("你好呀", "smalltalk_generated"), ("我要投诉！", None)):
        llm = ScriptedLLM()
        result = _ask(llm, message)
        assert result["observations"] == []
        assert llm.plan_calls == 0
        if expected:
            assert result["messages"][-1].content == expected
        else:
            assert result["human_fallback"] is True
            assert "400-000-0000" in result["messages"][-1].content


# ---------- 编排：工具由模型选，一轮可并行多个 ----------
def test_faq_question_goes_through_kb_search():
    llm = ScriptedLLM(steps=[[("kb_search", {"question": "运费怎么算"})]])
    result = _ask(llm, "运费怎么算")
    assert _tools(result) == ["kb_search"]
    assert result["messages"][-1].content == "agent_generated"
    assert "客服电话 400-000-0000" in llm.answer_prompts[0]


def test_schemas_cover_all_six_capabilities():
    llm = ScriptedLLM(steps=[])
    _ask(llm, "随便问问")
    names = {s["function"]["name"] for s in llm.bound_schemas}
    assert names == {"kb_search", "product_search", "order_query",
                     "logistics_query", "after_sales_query", "escalate_human"}


def test_multi_intent_calls_two_tools_in_one_turn():
    """一句话两个诉求（旧架构只能答一半）：同一步并行发两个 tool_calls，一次作答两件。"""
    llm = ScriptedLLM(steps=[[
        ("kb_search", {"question": "怎么申请退款"}),
        ("product_search", {"query": "50元以内的橙子", "keyword": "橙子", "maxPrice": 50, "inDomain": True}),
    ]])
    api = FakeJavaApi()
    result = _ask(llm, "怎么申请退款？另外有没有50元以内的橙子", api=api)
    assert _tools(result) == ["kb_search", "product_search"]
    assert api.last_search["max_price"] == 50.0            # 预算参数被带到 Java
    assert llm.plan_calls == 1                             # 两个诉求一步并行取完，不再追问
    assert [o["status"] for o in result["observations"]] == ["ok", "ok"]
    ctx = llm.answer_prompts[0]
    assert "客服电话 400-000-0000" in ctx and "赣南脐橙" in ctx  # 两份资料一起进上下文


def test_reflection_rewrites_query_after_empty_result():
    """空结果回灌成观察，模型改写检索表述再来一次（有界反思）。"""
    empty = {"tool": "kb_search", "kind": "kb", "status": "empty", "payload": "", "items": []}

    class WeakRetriever(StubRetriever):
        def retrieve(self, question, top_k=5):
            self.questions.append(question)
            if "秒杀" in question:
                return [{"id": 9, "question": "秒杀怎么玩", "answer": "整点开场，限量抢购", "score": 0.8}]
            return []

    llm = ScriptedLLM(steps=[
        [("kb_search", {"question": "这个怎么弄"})],
        [("kb_search", {"question": "秒杀活动怎么玩"})],
    ])
    ret = WeakRetriever()
    result = _graph(llm, retriever=ret).invoke(
        {"messages": [HumanMessage(content="这个怎么弄")], "userId": 1}
    )
    assert _tools(result) == ["kb_search", "kb_search"]
    assert ret.questions == ["这个怎么弄", "秒杀活动怎么玩"]  # 两次都真的拿去检索了
    # 第一次的空结果以 ToolMessage 形式回灌，模型才可能改写
    feedback = [m for m in llm.plan_inputs[1] if isinstance(m, ToolMessage)]
    assert feedback and json.loads(feedback[0].content)["status"] == "empty"


def test_duplicate_tool_call_is_skipped_not_reexecuted(monkeypatch):
    """模型原地打转（同样工具同样参数再来一遍）：不再执行，只回一条"已查过"。"""
    # 第三步留给"模型收到'已查过'之后收敛"：默认上限 2 步看不到那条回灌
    monkeypatch.setattr(settings, "AGENT_MAX_STEPS", 3)
    # 第一步空结果才会进第二步；第二步模型原样重复 → 只回灌"已查过"，不再执行
    llm = ScriptedLLM(steps=[
        [("kb_search", {"question": "查不到的问题"})],
        [("kb_search", {"question": "查不到的问题"})],
        [],
    ])
    ret = StubRetriever()
    result = _graph(llm, retriever=ret).invoke(
        {"messages": [HumanMessage(content="查不到的问题")], "userId": 1}
    )
    assert len(result["observations"]) == 1
    assert ret.questions == ["查不到的问题"]  # 只检索了一次
    dup = [m for m in llm.plan_inputs[2] if isinstance(m, ToolMessage)]
    assert json.loads(dup[-1].content)["status"] == "already_called"


def test_loop_is_bounded_by_max_steps():
    """全程空结果（永远触发反思重试）时，循环靠步数上限封顶，不依赖模型自觉。"""
    llm = ScriptedLLM(steps=[[("kb_search", {"question": f"查不到的问题{i}"})] for i in range(10)])
    result = _graph(llm).invoke({"messages": [HumanMessage(content="运费怎么算")], "userId": 1})
    assert llm.plan_calls == settings.AGENT_MAX_STEPS
    assert len(result["observations"]) == settings.AGENT_MAX_STEPS


def test_tool_calls_capped_per_turn():
    """一步里塞超过上限的调用：截断执行，防止额度被一次对话烧光。"""
    llm = ScriptedLLM(steps=[[("kb_search", {"question": f"问{i}"}) for i in range(settings.AGENT_MAX_TOOL_CALLS + 3)]])
    result = _graph(llm).invoke({"messages": [HumanMessage(content="运费怎么算")], "userId": 1})
    assert len(result["observations"]) == settings.AGENT_MAX_TOOL_CALLS


def test_loop_exits_early_when_material_is_already_usable():
    """取到可用资料就不再回规划器：实测网关单次规划 6~15 s，多问一轮等于首 token 翻倍。

    模型即使"忘了收敛"（还想再调一次），这里也已经收工了——循环上限与提前退出
    共同保证最坏情况有界，不依赖模型自觉。
    """
    llm = ScriptedLLM(steps=[
        [("order_query", {"orderNo": "AGS202609180001"})],
        [("order_query", {"orderNo": "AGS202609180002"})],  # 不该被执行
    ])
    result = _ask(llm, "订单 AGS202609180001 到哪了")
    assert _tools(result) == ["order_query"]
    assert llm.plan_calls == 1
    assert len(result["observations"]) == 1


# ---------- 业务工具与出口 ----------
def test_order_and_logistics_tools_use_order_no():
    llm = ScriptedLLM(steps=[[("order_query", {"orderNo": "AGS202609180001"})]])
    result = _ask(llm, "订单 AGS202609180001 现在什么状态")
    assert "AGS202609180001" in result["observations"][0]["payload"]
    assert result["messages"][-1].content == "agent_generated"


def test_business_tool_without_login_returns_guidance_without_llm():
    """未登录查订单：直接给登录引导，不经 LLM——既省一次调用，也不给它编造订单的机会。"""
    llm = ScriptedLLM(steps=[[("order_query", {})]] )
    result = _ask(llm, "我的订单到哪了", user_id=None)
    assert result["observations"][0]["status"] == "need_login"
    assert llm.plan_calls == 1  # 未登录是确定性出口，不再多问一次模型
    assert "请到小程序登录后咨询" in result["messages"][-1].content
    assert "400-000-0000" in result["messages"][-1].content
    assert llm.answer_prompts == []


def test_escalate_human_tool_ends_in_human_reply():
    llm = ScriptedLLM(steps=[[("escalate_human", {"reason": "用户要求人工跟进"})]])
    result = _ask(llm, "把我买到的坏果赔了，还要找领导")
    assert result["human_fallback"] is True
    assert "400-000-0000" in result["messages"][-1].content


def test_human_escalation_is_announced_even_when_other_tools_succeeded():
    """转人工没有"资料"，但它是必须告知用户的事实：与其他成功观察一起进上下文，
    否则模型只看到订单结果，用户就不知道自己已经被转人工了。"""
    llm = ScriptedLLM(steps=[[("order_query", {"orderNo": "AGS202609180001"}),
                              ("escalate_human", {"reason": "用户要求人工跟进投诉"})]])
    # 措辞刻意避开"投诉/人工"等高置信诉求词：这类句子会被规则前置直接短路，
    # 走不到智能体，也就测不到"转人工观察与其他资料并存"这条路
    result = _ask(llm, "这单迟迟不到，你们看着办吧")
    assert result["human_fallback"] is True
    assert "escalate_human" in llm.answer_prompts[0]
    assert "AGS202609180001" in llm.answer_prompts[0]


def test_planner_is_told_which_product_the_user_is_browsing():
    """E2E 实测：规划器不知道用户在浏览哪件商品时，"这个适合送人吗"会因为指代补不出来
    而一个工具都不调，作答节点只能反问"您是指哪款产品呀"——商详页进线的价值就没了。
    商品事实必须进规划上下文，并带上 keyword 的填法（留空=指代眼前这件）。"""
    class BrowseApi(FakeJavaApi):
        def fetch_product(self, product_id):
            return {"id": 900, "name": "赣南脐橙 10斤", "price": 19.9,
                    "storage": "阴凉干燥处", "shelfLife": "7天", "status": 1}

    llm = ScriptedLLM(steps=[[("product_search", {"query": "这个适合送人吗"})]])
    _graph(llm, api=BrowseApi()).invoke({
        "messages": [HumanMessage(content="这个适合送人吗")], "userId": 1, "productId": 900})
    plan_system = llm.plan_inputs[0][0].content          # 规划器看到的 system prompt
    assert "用户当前正在浏览的商品" in plan_system
    assert "赣南脐橙 10斤" in plan_system
    assert "不要填 keyword" in plan_system


def test_out_of_domain_recommendation_refuses_without_llm():
    """域外导购（"推荐一双跑步鞋"）经智能体也一样拒：闸门在执行器里，与谁发起调用无关。

    这是量化实验五的回归：0.35 闸门下 10 条域外请求放行 6 条，旧行为会硬端上金华火腿。
    """
    class RefuseApi(FakeJavaApi):
        def search_products(self, **kw):
            return []

    llm = ScriptedLLM(steps=[[("product_search",
                               {"query": "推荐一双跑步鞋", "keyword": "跑步鞋", "inDomain": False})]])
    result = _ask(llm, "推荐一双跑步鞋", api=RefuseApi())
    assert result["observations"][0]["status"] == "refused"
    assert result["rec_items"] == []
    reply = result["messages"][-1].content
    assert "农产品导购助手" in reply and "400-000-0000" in reply
    assert "赣南脐橙" not in reply and llm.answer_prompts == []  # 不经 LLM、不硬塞商品


def test_browsing_product_answers_with_card():
    """商详页进客服问"这个能放多久"：语义/LIKE 都无命中时按商品事实回答，并给出这件的卡片。"""
    class BrowseApi(FakeJavaApi):
        def fetch_product(self, product_id):
            return {"id": 900, "name": "赣南脐橙 10斤", "price": 19.9,
                    "storage": "阴凉干燥处", "shelfLife": "7天", "status": 1}

    llm = ScriptedLLM(steps=[[("product_search", {"query": "这个能放多久"})]])
    graph = _graph(llm, api=BrowseApi())
    result = graph.invoke({
        "messages": [HumanMessage(content="这个能放多久")],
        "userId": 1, "productId": 900,
    })
    assert [p["id"] for p in result["rec_items"]] == [900]
    assert result["observations"][0]["kind"] == "product_qa"
    assert llm.plan_calls == 1                       # 商品事实可用 → 不再回规划器
    assert "保质期：7天" in llm.answer_prompts[0]        # 商品事实进作答上下文
    assert "这件商品" in llm.answer_prompts[0]           # 用的是商品问答提示词（含指代约束）


def test_retriever_failure_degrades_friendly_not_raw_error():
    """检索器炸了：只能给出友好降级话术，内部异常字样一个都不许进用户端。"""

    class BoomRetriever:
        def retrieve(self, question, top_k=5):
            raise RuntimeError("chroma 连接失败 http://127.0.0.1:8000/bge-large-zh 429")

    llm = ScriptedLLM(steps=[[("kb_search", {"question": "运费怎么算"})]])
    result = _graph(llm, retriever=BoomRetriever()).invoke(
        {"messages": [HumanMessage(content="运费怎么算")], "userId": 1}
    )
    reply = result["messages"][-1].content
    assert result["observations"][0]["status"] == "error"
    assert result["observations"][0]["payload"] == ""   # 细节没进观察
    for leak in ("chroma", "127.0.0.1", "429", "bge-large"):
        assert leak not in reply
    assert "400-000-0000" in reply


def test_unknown_tool_from_model_does_not_break_answer():
    llm = ScriptedLLM(steps=[[("send_parcel", {"no": "1"})]])
    result = _ask(llm, "我的订单到哪了")
    assert result["observations"][0]["status"] == "error"
    assert result["messages"][-1].content  # 仍有回复，不白屏


# ---------- 能力协商与降级路径 ----------
@pytest.mark.parametrize("broken", ["no_tools", "plan_error"])
def test_degraded_path_still_uses_one_tool(broken):
    """上游不支持 function calling / 规划调用炸了 → 退回"分类 + 单工具"，功能不回退。

    降级是"慢一点、一次只答一个诉求"，不是"不回话"：这条路径必须继续能取证。
    """
    llm = ScriptedLLM(
        support_tools=(broken != "no_tools"),
        plan_error=(broken == "plan_error"),
        intent_json=json.dumps({"intent": "order", "order_no": None}),
    )
    result = _ask(llm, "我的订单什么情况")
    assert _tools(result) == ["order_query"]
    assert "AGS202609180001" in result["observations"][0]["payload"]
    assert result["messages"][-1].content == "agent_generated"


def test_degraded_path_carries_recommend_preferences_in_one_call():
    """降级路径同样只有"意图 + 偏好"这一次 LLM 调用（合并前是两次串行）。"""
    intent = {"intent": "recommend", "order_no": None,
              "keyword": "水果", "maxPrice": 50, "inDomain": True}
    llm = ScriptedLLM(support_tools=False, intent_json=json.dumps(intent, ensure_ascii=False))
    api = FakeJavaApi()
    result = _ask(llm, "推荐50元以内的水果", api=api)
    assert _tools(result) == ["product_search"]
    assert api.last_search == {"keyword": "水果", "max_price": 50.0, "ids": None}
    assert llm.plan_calls == 0            # 规划器没被调用（走的就是降级分支）
    assert len(llm.answer_prompts) == 1   # 只有作答这一次 LLM


def test_degraded_path_answers_guidance_for_smalltalk():
    llm = ScriptedLLM(support_tools=False,
                      intent_json=json.dumps({"intent": "smalltalk", "order_no": None}))
    result = _ask(llm, "谢谢啦，那运费呢")
    assert result["observations"] == []
    assert result["messages"][-1].content == "smalltalk_generated"


def test_keyword_fallback_classify():
    # LLM 返回非法 JSON 时走关键词兜底
    class BadLLM:
        def invoke(self, messages):
            return AIMessage(content="我不懂")

    intent, order_no = classify_intent("我的快递到哪了", BadLLM())
    assert intent == "logistics"


# ---------- 跨轮状态隔离（持久化 checkpointer 下的污染回归）----------
def test_observations_not_carried_into_next_turn():
    """上一轮订单 JSON 不得污染下一轮问答：
    state 经 checkpointer 按 thread_id 持久化，intent 节点若不清理 observations，
    本轮生成会先读到上一轮订单数据当上下文（答非所问 + 复述上一轮隐私）。
    """
    from langgraph.checkpoint.memory import MemorySaver

    # 每轮一步取完（资料可用即退出），脚本一步一条
    llm = ScriptedLLM(steps=[
        [("order_query", {"orderNo": "AGS202609180001"})],
        [("kb_search", {"question": "运费怎么算"})],
    ])
    graph = _graph(llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "cross-turn"}}

    r1 = graph.invoke({"messages": [HumanMessage(content="我的订单什么情况")], "userId": 1}, config)
    assert _tools(r1) == ["order_query"]

    llm.answer_prompts.clear()
    r2 = graph.invoke({"messages": [HumanMessage(content="运费怎么算的")], "userId": 1}, config)
    assert _tools(r2) == ["kb_search"]
    assert r2["observations"] and all("AGS202609180001" not in json.dumps(o) for o in r2["observations"])
    assert "AGS202609180001" not in llm.answer_prompts[0]  # 订单号不进本轮 prompt


# ---------- 提示词约束：语言、卡片顺序与反幻觉 ----------
def test_prompts_require_simplified_chinese():
    """E2E 实测：商品问答首轮回复跑成了俄汉混排——模板漏了语言约束。
    上游模型会随事实文本的语言漂移，作答类提示词必须钉死简体中文。"""
    from app.prompts import (
        AGENT_ANSWER_SYSTEM_TEMPLATE,
        PRODUCT_QA_SYSTEM_TEMPLATE,
    )

    for name, tpl in (("智能体作答", AGENT_ANSWER_SYSTEM_TEMPLATE), ("商品问答", PRODUCT_QA_SYSTEM_TEMPLATE)):
        assert "简体中文" in tpl, name


def test_agent_answer_prompt_requires_candidate_order():
    """卡片按服务端相关度原序渲染，LLM 若跳着讲，用户看到的顺序就和话术对不上。"""
    from app.prompts import AGENT_ANSWER_SYSTEM_TEMPLATE

    assert "候选的先后顺序" in AGENT_ANSWER_SYSTEM_TEMPLATE
    assert "positiveRate" in AGENT_ANSWER_SYSTEM_TEMPLATE   # 口碑照抄、禁虚构
    assert "严禁编造" in AGENT_ANSWER_SYSTEM_TEMPLATE


def test_answer_prompt_forbids_praise_in_place_of_numbers():
    """E2E 实测：3 条评价、好评率 66.7% 的商品被模型说成"口碑不错"。
    演示文案里每一个评价性说法都必须能回到数值，替用户下结论就是编造。

    现在更硬一层：口碑句子由服务端拼好放在 reputation 字段里，模型只能一字不改地引用
    （见 app/tools/java_api.py:reputation_text 与 tests/test_reputation_text.py）。
    """
    from app.prompts import AGENT_ANSWER_SYSTEM_TEMPLATE

    assert "一字不改地引用" in AGENT_ANSWER_SYSTEM_TEMPLATE
    assert "reputation" in AGENT_ANSWER_SYSTEM_TEMPLATE
    assert "不得改写" in AGENT_ANSWER_SYSTEM_TEMPLATE      # 不许合并、不许换说法
    assert "口碑不错" in AGENT_ANSWER_SYSTEM_TEMPLATE       # 明确列为禁句
    assert "严禁编造" in AGENT_ANSWER_SYSTEM_TEMPLATE
    # E2E 实测第二轮：模型把 topKeywords 里的"回购"讲成"回购率也不错"——词不是指标
    # 第三轮：sales=1046 被讲成"1046 人回购"。销量与回购人数是两件事
    assert "已售 N 件" in AGENT_ANSWER_SYSTEM_TEMPLATE


def test_plan_prompt_forbids_writing_the_reply():
    """规划器只选工具不写回复：否则它的中间输出会污染 token 流与额度。"""
    from app.prompts import AGENT_PLAN_SYSTEM_PROMPT

    assert "不要自己写回复" in AGENT_PLAN_SYSTEM_PROMPT
    assert "并行调用多个工具" in AGENT_PLAN_SYSTEM_PROMPT
