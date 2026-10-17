"""Agent 图测试（阶段三）：意图路由 + 各分支节点，全部 stub，不依赖网络"""
import json

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.agent.graph import build_graph
from app.agent.intent import classify_intent
from app.agent.nodes import route_by_intent
from app.agent.state import AgentState


class StubRetriever:
    def retrieve(self, question, top_k=5):
        return [{"id": 1, "question": "怎么联系人工客服？", "answer": "客服电话 400-000-0000", "score": 0.9}]


class FakeJavaApi:
    """模拟 Java 内部接口返回"""

    def query_orders(self, user_id, status=None):
        return [{"orderNo": "AGS202609180001", "status": 2, "statusText": "待收货", "payAmount": 39.9}]

    def query_order_detail(self, user_id, order_no):
        return {"orderNo": order_no, "status": 2, "statusText": "待收货", "logType": "顺丰", "logNo": "SF123456"}

    def query_logistics(self, user_id, order_no):
        return {"orderNo": order_no, "status": 2, "statusText": "待收货", "logType": "顺丰", "logNo": "SF123456"}

    def query_after_sales(self, user_id, status=None):
        return [{"afterSalesNo": "AS202609180001", "status": 0, "statusText": "待处理", "refundAmount": 20}]

    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
        self.last_search = {"keyword": keyword, "min_price": min_price, "max_price": max_price, "limit": limit}
        if keyword == "不存在的品类":
            return []
        return [
            {"id": 1, "name": "赣南脐橙", "price": 19.9, "sales": 500, "origin": "江西赣州"},
            {"id": 2, "name": "五常大米", "price": 68.0, "sales": 300, "origin": "黑龙江五常"},
        ]

    def format_payload(self, data):
        return json.dumps(data, ensure_ascii=False)


def make_llm(intent_json: str):
    class _LLM:
        def invoke(self, messages):
            for m in messages:
                if isinstance(m, SystemMessage):
                    if "意图识别器" in m.content:
                        return AIMessage(content=intent_json)
                    if "知识库内容" in m.content:
                        return AIMessage(content="faq_generated")
                    if "查询结果" in m.content:
                        return AIMessage(content="tool_generated")
                    if "智能导购" in m.content:
                        return AIMessage(content="recommend_generated")
                    if "偏好抽取器" in m.content:
                        return AIMessage(content=json.dumps({"keyword": "水果", "maxPrice": 50}, ensure_ascii=False))
                    if "闲聊" in m.content:
                        return AIMessage(content="smalltalk_generated")
            return AIMessage(content="no_context")

    return _LLM()


def _run(intent: str, message: str = "test", user_id: int | None = 1):
    graph = build_graph(
        llm=make_llm(json.dumps({"intent": intent, "order_no": None}, ensure_ascii=False)),
        retriever=StubRetriever(),
        java_api_module=FakeJavaApi(),
        checkpointer=None,  # 单测不需要多轮记忆
    )
    return graph.invoke({"messages": [HumanMessage(content=message)], "userId": user_id})


def test_route_by_intent_mapping():
    assert route_by_intent({"intent": "order"}) == "order_query"
    assert route_by_intent({"intent": "logistics"}) == "logistics_query"
    assert route_by_intent({"intent": "after_sales"}) == "after_sales_query"
    assert route_by_intent({"intent": "recommend"}) == "recommend_query"
    assert route_by_intent({"intent": "smalltalk"}) == "smalltalk"
    assert route_by_intent({"intent": "human"}) == "human_fallback"
    assert route_by_intent({"intent": "faq"}) == "faq_retrieve"
    assert route_by_intent({}) == "faq_retrieve"


def test_faq_route_generates_from_context():
    result = _run("faq", "客服电话是多少")
    assert result["messages"][-1].content == "faq_generated"
    assert result["intent"] == "faq"


def test_order_route_calls_java_api():
    result = _run("order", "我的订单到哪了")
    assert result["intent"] == "order"
    assert result["tool_type"] == "order"
    assert "AGS202609180001" in result["tool_result"]
    assert result["messages"][-1].content == "tool_generated"


def test_logistics_route():
    result = _run("logistics", "物流到哪了")
    assert result["tool_type"] == "logistics"
    assert "SF123456" in result["tool_result"]


def test_after_sales_route():
    result = _run("after_sales", "退款到哪了")
    assert result["tool_type"] == "after_sales"
    assert "AS202609180001" in result["tool_result"]


def test_human_fallback_route():
    result = _run("human", "我要投诉！")
    assert result["human_fallback"] is True
    assert "400-000-0000" in result["messages"][-1].content


def test_smalltalk_route():
    result = _run("smalltalk", "你好呀")
    assert result["messages"][-1].content == "smalltalk_generated"


def test_keyword_fallback_classify():
    # LLM 返回非法 JSON 时走关键词兜底
    class BadLLM:
        def invoke(self, messages):
            return AIMessage(content="我不懂")

    intent, order_no = classify_intent("我的快递到哪了", BadLLM())
    assert intent == "logistics"


# ---------- 阶段六：智能导购 ----------
def test_recommend_route_calls_java_api():
    """导购：偏好与意图同一次产出（水果/50 元）→ 搜索 → 候选商品进入生成节点"""
    api = FakeJavaApi()
    result = _run_with_api(
        "recommend", "帮我推荐50元以内的水果", api,
        prefs={"keyword": "水果", "maxPrice": 50, "inDomain": True},
    )
    assert result["tool_type"] == "recommend"
    assert "赣南脐橙" in result["tool_result"]
    # 偏好被正确抽取并透传给搜索接口
    assert api.last_search["keyword"] == "水果"
    assert api.last_search["max_price"] == 50.0
    assert result["messages"][-1].content == "recommend_generated"
    assert result["suggestions"]  # recommend 意图有小贴士


def test_recommend_empty_result():
    """品类词无命中但属经营范围 → 回退热销榜并在上下文注明原因（而非直接返回空）"""

    class EmptyApi(FakeJavaApi):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
            self.last_search = {"keyword": keyword, "min_price": min_price, "max_price": max_price, "limit": limit}
            if keyword:  # 任何品类词都无命中
                return []
            return [{"id": 1, "name": "赣南脐橙", "price": 39.9, "sales": 500, "origin": "江西赣州"}]

    api = EmptyApi()
    result = _run_with_api(
        "recommend", "推荐点不存在的品类", api,
        prefs={"keyword": "不存在的品类", "maxPrice": None, "inDomain": True},
    )
    assert result["tool_type"] == "recommend"
    assert "未找到" in result["tool_result"]  # 原因说明
    assert "赣南脐橙" in result["tool_result"]  # 热销候选兜底
    assert api.last_search["keyword"] is None  # 第二次搜索已去掉品类词


def test_recommend_out_of_domain_returns_deterministic_refusal():
    """域外导购请求（"推荐一双跑步鞋"）：语义无命中 + LIKE 无命中 + 模型判定不属经营范围
    → 确定性拒答，不经 LLM、不降级热销榜。

    这是量化实验五的直接回归：0.35 闸门下 10 条域外请求放行 6 条，
    旧行为会把"金华火腿"当跑步鞋推荐给用户。
    """
    calls = []

    class RefuseApi(FakeJavaApi):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
            calls.append(keyword)
            return []  # 跑步鞋 LIKE 无命中

    class ExplodingGenerateLLM:
        """生成节点若被调用就炸——用来证明拒答路径一次 LLM 都没花"""

        def invoke(self, messages):
            for m in messages:
                if isinstance(m, SystemMessage) and "意图识别器" in m.content:
                    return AIMessage(content='{"intent": "recommend", "order_no": null,'
                                             ' "keyword": "跑步鞋", "maxPrice": null, "inDomain": false}')
            raise AssertionError("拒答路径不应调用生成 LLM")

    graph = build_graph(
        llm=ExplodingGenerateLLM(), retriever=StubRetriever(),
        java_api_module=RefuseApi(), checkpointer=None,
    )
    result = graph.invoke({"messages": [HumanMessage(content="推荐一双跑步鞋")], "userId": 1})

    assert result["tool_type"] == "recommend_refused"
    assert calls == ["跑步鞋"]  # 查过 LIKE，确认库里没有
    reply = result["messages"][-1].content
    assert "农产品导购助手" in reply and "400-000-0000" in reply
    assert "金华火腿" not in reply and "赣南脐橙" not in reply  # 绝不硬塞商品


def test_recommend_no_judgement_keeps_hot_fallback():
    """没有拿到经营域判定（inDomain 缺失/为 null，如 LLM 故障走兜底）时不得拒答，
    行为与改动前一致：热销榜兜底，路由不中断。"""
    api = FakeJavaApi()
    result = _run_with_api("recommend", "推荐点东西", api)  # 意图 JSON 不含任何偏好字段
    assert result["tool_type"] == "recommend"
    assert api.last_search["keyword"] is None
    assert "赣南脐橙" in result["tool_result"]


def test_recommend_in_domain_vague_request_still_gets_hot_list():
    """泛推荐需求（"有什么好物"，inDomain=true 且无品类词）属正常业务，继续给热销榜"""
    api = FakeJavaApi()
    result = _run_with_api(
        "recommend", "有什么好物", api,
        prefs={"keyword": None, "maxPrice": None, "inDomain": True},
    )
    assert result["tool_type"] == "recommend"
    assert "赣南脐橙" in result["tool_result"]
    assert "未找到" not in result["tool_result"]  # 没有品类词就不该编造"未找到"说明


def test_recommend_like_hit_wins_over_out_of_domain_label():
    """模型判 inDomain=false，但 LIKE 确实在卖这件商品 → 以正面证据为准，正常推荐不拒答"""

    class SellsItApi(FakeJavaApi):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
            self.last_search = {"keyword": keyword, "min_price": min_price, "max_price": max_price, "limit": limit}
            if keyword == "鸡胸肉":
                return [{"id": 3, "name": "鸡胸肉 低脂", "price": 25.0, "sales": 80, "origin": "山东"}]
            return []

    api = SellsItApi()
    result = _run_with_api(
        "recommend", "推荐个附近的健身房", api,
        prefs={"keyword": "鸡胸肉", "maxPrice": None, "inDomain": False},
    )
    assert result["tool_type"] == "recommend"
    assert "鸡胸肉 低脂" in result["tool_result"]


def test_recommend_prefs_fallback_when_llm_fails():
    """意图 LLM 整体失败 → 关键词兜底给出 recommend，偏好字段全空（= 没有判定）
    → 按销量推荐热销商品，路由与回复都不中断（故障期行为与合并前一致）。"""

    class BadLLM:
        def invoke(self, messages):
            content = m.content if isinstance(m := messages[0], SystemMessage) else ""
            if "意图识别器" in content:
                raise RuntimeError("429 Too Many Requests")
            if "智能导购" in content:
                return AIMessage(content="recommend_generated")
            return AIMessage(content="no_context")

    api = FakeJavaApi()
    graph = build_graph(llm=BadLLM(), retriever=StubRetriever(), java_api_module=api, checkpointer=None)
    result = graph.invoke({"messages": [HumanMessage(content="有什么水果推荐")], "userId": 1})
    assert result["intent"] == "recommend"  # 由关键词兜底保住路由
    assert api.last_search["keyword"] is None  # 没有偏好判定
    assert "赣南脐橙" in result["tool_result"]


def test_recommend_keyword_fallback_classify():
    class BadLLM:
        def invoke(self, messages):
            return AIMessage(content="我不懂")

    intent, _ = classify_intent("有什么水果推荐", BadLLM())
    assert intent == "recommend"


def _run_with_api(intent: str, message: str, api, prefs: dict | None = None) -> dict:
    """prefs：导购偏好，现与意图在**同一次 LLM 调用**里产出（合并前是独立第二次调用）"""
    payload = {"intent": intent, "order_no": None}
    payload.update(prefs or {})
    graph = build_graph(
        llm=make_llm(json.dumps(payload, ensure_ascii=False)),
        retriever=StubRetriever(),
        java_api_module=api,
        checkpointer=None,
    )
    return graph.invoke({"messages": [HumanMessage(content=message)], "userId": 1})


# ---------- 阶段八：知识库未覆盖 → 确定性拒答 ----------
def test_kb_miss_returns_deterministic_refusal():
    """检索零命中（阈值拒答后）→ 生成节点不经 LLM 直接返回拒答话术（零幻觉、省额度）"""

    class EmptyRetriever:
        def retrieve(self, question, top_k=5):
            return []

    graph = build_graph(
        llm=make_llm(json.dumps({"intent": "faq", "order_no": None}, ensure_ascii=False)),
        retriever=EmptyRetriever(),
        java_api_module=FakeJavaApi(),
        checkpointer=None,
    )
    result = graph.invoke({"messages": [HumanMessage(content="怎么看今天的股市行情")], "userId": 1})
    reply = result["messages"][-1].content
    assert "知识库还未覆盖" in reply
    assert "400-000-0000" in reply
    assert reply != "faq_generated"  # 未走 LLM 生成路径
    assert result["suggestions"]  # 拒答后仍给出下一轮建议问题


def test_kb_hit_still_uses_llm():
    """检索有命中 → 正常走 LLM 生成（回归保护）"""
    result = _run("faq", "客服电话是多少")
    assert result["messages"][-1].content == "faq_generated"


# ---------- A3：导购接入口碑数据 ----------
def test_recommend_context_carries_reputation_fields():
    """口碑字段（好评率/评价数/好评关键词）随候选商品进入生成节点上下文"""
    captured = {}

    class RecordingLLM:
        def invoke(self, messages):
            for m in messages:
                if not isinstance(m, SystemMessage):
                    continue
                if "意图识别器" in m.content:
                    return AIMessage(content='{"intent": "recommend", "order_no": null}')
                if "偏好抽取器" in m.content:
                    return AIMessage(content=json.dumps({"keyword": "水果", "maxPrice": 50}, ensure_ascii=False))
                if "智能导购" in m.content:
                    captured["ctx"] = m.content
                    return AIMessage(content="recommend_generated")
            return AIMessage(content="no_context")

    class ReputationApi(FakeJavaApi):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
            return [{
                "id": 1, "name": "赣南脐橙", "price": 19.9, "sales": 500, "origin": "江西赣州",
                "reviewCount": 120, "positiveRate": 98.5, "topKeywords": ["新鲜", "甜"],
            }]

    graph = build_graph(llm=RecordingLLM(), retriever=StubRetriever(), java_api_module=ReputationApi(), checkpointer=None)
    result = graph.invoke({"messages": [HumanMessage(content="推荐点水果")], "userId": 1})

    assert result["messages"][-1].content == "recommend_generated"
    ctx = captured["ctx"]
    assert '"reviewCount": 120' in ctx
    assert '"positiveRate": 98.5' in ctx
    assert "新鲜" in ctx


def test_recommend_prompt_has_reputation_rules():
    """导购 prompt 要求引用真实口碑、禁止编造（反幻觉约束随口碑数据一起引入）"""
    from app.prompts import RECOMMEND_SYSTEM_PROMPT_TEMPLATE

    assert "positiveRate" in RECOMMEND_SYSTEM_PROMPT_TEMPLATE
    assert "reviewCount" in RECOMMEND_SYSTEM_PROMPT_TEMPLATE
    assert "严禁编造或夸大" in RECOMMEND_SYSTEM_PROMPT_TEMPLATE


# ---------- 阶段五：降级与重试 ----------
def test_generate_degraded_when_llm_fails():
    """Agnes 调用失败（限流/超时/断网）→ 生成节点返回降级话术，不抛异常不白屏"""

    class FailingLLM:
        def invoke(self, messages):
            raise RuntimeError("429 Too Many Requests")

    graph = build_graph(
        llm=FailingLLM(),
        retriever=StubRetriever(),
        checkpointer=None,
    )
    result = graph.invoke({"messages": [HumanMessage(content="怎么申请退款？")], "userId": 1})
    reply = result["messages"][-1].content
    assert "客服电话" in reply
    assert "400-000-0000" in reply
    assert "429" not in reply  # 不向用户暴露技术细节


def test_smalltalk_degraded_when_llm_fails():
    class FailingLLM:
        def invoke(self, messages):
            raise TimeoutError("timeout")

    graph = build_graph(llm=FailingLLM(), retriever=StubRetriever(), checkpointer=None)
    result = graph.invoke({"messages": [HumanMessage(content="你好")], "userId": 1})
    assert "400-000-0000" in result["messages"][-1].content


def test_llm_retry_param_present():
    """ChatOpenAI 构造应带 max_retries（429/5xx 自动退避）——通过 get_llm 工厂验证配置存在"""
    from app.config import settings

    assert settings.LLM_MAX_RETRIES >= 1
    assert settings.LLM_TIMEOUT > 0


# ---------- 跨轮工具结果隔离（持久化 checkpointer 下的状态污染回归）----------
def test_tool_result_not_carried_into_next_faq_turn():
    """上一轮订单 JSON 不得污染下一轮 FAQ 问答：
    state 经 checkpointer 按 thread_id 持久化，intent 节点若不清理 tool_result/tool_type，
    FAQ 轮的生成节点会先读到上一轮订单数据当 system prompt（答非所问 + 复述上一轮隐私）。
    """
    from langgraph.checkpoint.memory import MemorySaver

    prompts: list[str] = []

    class TurnAwareLLM:
        def invoke(self, messages):
            last_human = [m for m in messages if isinstance(m, HumanMessage)][-1].content
            for m in messages:
                if not isinstance(m, SystemMessage):
                    continue
                if "意图识别器" in m.content:
                    intent = "order" if "订单" in last_human else "faq"
                    return AIMessage(content=json.dumps({"intent": intent, "order_no": None}))
                if "知识库内容" in m.content or "查询结果" in m.content:
                    prompts.append(m.content)
                    return AIMessage(content="generated")
            return AIMessage(content="generated")

    graph = build_graph(
        llm=TurnAwareLLM(),
        retriever=StubRetriever(),
        java_api_module=FakeJavaApi(),
        checkpointer=MemorySaver(),
    )
    config = {"configurable": {"thread_id": "cross-turn"}}

    r1 = graph.invoke({"messages": [HumanMessage(content="我的订单什么情况")], "userId": 1}, config)
    assert r1["tool_type"] == "order"
    assert "AGS202609180001" in r1["tool_result"]

    prompts.clear()
    r2 = graph.invoke({"messages": [HumanMessage(content="运费怎么算的")], "userId": 1}, config)
    assert r2["intent"] == "faq"
    assert not r2["tool_result"]  # 本轮无工具产物，不得残留上一轮
    assert prompts and "知识库内容" in prompts[0]  # 生成节点用的是 FAQ 上下文
    assert all("AGS202609180001" not in p for p in prompts)  # 订单号不进入本轮 prompt

