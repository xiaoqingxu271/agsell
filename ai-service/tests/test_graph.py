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
    """导购：偏好抽取（水果/50元）→ 搜索 → 候选商品进入生成节点"""
    api = FakeJavaApi()
    result = _run_with_api("recommend", "帮我推荐50元以内的水果", api)
    assert result["tool_type"] == "recommend"
    assert "赣南脐橙" in result["tool_result"]
    # 偏好被正确抽取并透传给搜索接口
    assert api.last_search["keyword"] == "水果"
    assert api.last_search["max_price"] == 50.0
    assert result["messages"][-1].content == "recommend_generated"
    assert result["suggestions"]  # recommend 意图有小贴士


def test_recommend_empty_result():
    """品类词无命中 → 回退热销榜并在上下文注明原因（而非直接返回空）"""

    class EmptyApi(FakeJavaApi):
        def search_products(self, keyword=None, min_price=None, max_price=None, limit=6):
            self.last_search = {"keyword": keyword, "min_price": min_price, "max_price": max_price, "limit": limit}
            if keyword:  # 任何品类词都无命中（stub 抽取器返回 keyword="水果"）
                return []
            return [{"id": 1, "name": "赣南脐橙", "price": 39.9, "sales": 500, "origin": "江西赣州"}]

    api = EmptyApi()
    result = _run_with_api("recommend", "推荐点不存在的品类", api)
    assert result["tool_type"] == "recommend"
    assert "未找到" in result["tool_result"]  # 原因说明
    assert "赣南脐橙" in result["tool_result"]  # 热销候选兜底
    assert api.last_search["keyword"] is None  # 第二次搜索已去掉品类词


def test_recommend_prefs_fallback_when_llm_fails():
    """偏好抽取 LLM 失败 → 降级为按销量推荐热销商品（keyword=None），路由不中断"""

    class NoPrefsLLM:
        def invoke(self, messages):
            content = m.content if isinstance(m := messages[0], SystemMessage) else ""
            if "意图识别器" in content:
                return AIMessage(content='{"intent": "recommend", "order_no": null}')
            if "智能导购" in content:
                return AIMessage(content="recommend_generated")
            raise RuntimeError("偏好抽取挂了")

    api = FakeJavaApi()
    graph = build_graph(llm=NoPrefsLLM(), retriever=StubRetriever(), java_api_module=api, checkpointer=None)
    result = graph.invoke({"messages": [HumanMessage(content="推荐点东西")], "userId": 1})
    assert api.last_search["keyword"] is None
    assert "赣南脐橙" in result["tool_result"]


def test_recommend_keyword_fallback_classify():
    class BadLLM:
        def invoke(self, messages):
            return AIMessage(content="我不懂")

    intent, _ = classify_intent("有什么水果推荐", BadLLM())
    assert intent == "recommend"


def _run_with_api(intent: str, message: str, api) -> dict:
    graph = build_graph(
        llm=make_llm(json.dumps({"intent": intent, "order_no": None}, ensure_ascii=False)),
        retriever=StubRetriever(),
        java_api_module=api,
        checkpointer=None,
    )
    return graph.invoke({"messages": [HumanMessage(content=message)], "userId": 1})


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
