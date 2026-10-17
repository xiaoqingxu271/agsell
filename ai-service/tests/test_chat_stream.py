"""SSE 流式对话接口测试：stub LLM，不依赖网络与 API Key

覆盖：
1. 正常流式：token 逐字下发 + status 阶段（含本轮真正调用了哪些工具）+ items 卡片 + done 收尾；
2. 智能体内部的规划输出（tool_calls、空内容）绝不被当成回复 token 下发；
3. 依赖故障：检索器炸了 → 图内友好降级；图整体抛异常 → error 帧只带 code；
4. thread key 按服务端可信 userId 加命名空间（同一 sessionId 不跨用户串上下文）。
"""
import json
import os

from fastapi.testclient import TestClient
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.checkpoint.memory import MemorySaver

from app.agent.graph import build_graph

INTENT_SMALLTALK = json.dumps({"intent": "smalltalk", "order_no": None}, ensure_ascii=False)
INTENT_FAQ = json.dumps({"intent": "faq", "order_no": None}, ensure_ascii=False)
REPLY_TEXT = "您好！很高兴为您服务，请问有什么可以帮您？"
_CARD = [{"id": 2, "name": "赣南脐橙", "price": 19.9, "mainImage": "https://img/2.jpg",
          "origin": "江西赣州", "sales": 500, "reviewCount": 12, "positiveRate": 98.5}]


class AgentFakeChatModel(GenericFakeChatModel):
    """GenericFakeChatModel + bind_tools：让流式测试跑的是智能体路径，而不是静默走降级分支。

    消息队列按「规划步 → 收敛步（空 tool_calls）→ 作答」排布；
    规划器返回的 tool_calls/空内容都不是文本 token，天然验证了"内部输出不外显"。
    """

    def bind_tools(self, tools, **kwargs):  # noqa: ARG002 - 替身不需要真的校验 schema
        return self

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        """父类只把 content（和 additional_kwargs）切成 chunk，tool_calls 会在这一步丢掉。

        真实网关是通过 tool_call_chunks 增量传工具调用的，所以替身也照这个形态给：
        否则图里收到的规划结果永远没有 tool_calls，测的就不是智能体路径了。
        """
        import re

        from langchain_core.messages import AIMessage, AIMessageChunk
        from langchain_core.outputs import ChatGenerationChunk

        chat_result = self._generate(messages, stop=stop, run_manager=run_manager, **kwargs)
        message = chat_result.generations[0].message
        if isinstance(message, AIMessage) and message.tool_calls:
            yield ChatGenerationChunk(message=AIMessageChunk(
                content="", id=message.id,
                tool_call_chunks=[
                    {"name": tc["name"], "args": json.dumps(tc["args"], ensure_ascii=False),
                     "id": tc.get("id") or f"call{i}", "index": i}
                    for i, tc in enumerate(message.tool_calls)
                ],
            ))
            return
        # 作答步：与父类一致按空白切词，模拟逐字下发；空内容也至少给一个 chunk
        parts = [t for t in re.split(r"(\s)", message.content or "") if t != ""] or [""]
        for token in parts:
            yield ChatGenerationChunk(message=AIMessageChunk(content=token, id=message.id))


def _kb_plan(question: str) -> AIMessage:
    return AIMessage(content="", tool_calls=[{"name": "kb_search", "args": {"question": question}, "id": "c1"}])


def _rec_plan(query: str, **args) -> AIMessage:
    return AIMessage(content="", tool_calls=[
        {"name": "product_search", "args": {"query": query, **args}, "id": "c1"}])


class _StubRetriever:
    def retrieve(self, question, top_k=5):
        return [{"id": 1, "question": "运费怎么算", "answer": "按重量计费，满额包邮", "score": 0.9}]


def _client(monkeypatch, llm, checkpointer=None, retriever=None, java_api_module=None):
    import app.main as main_mod

    graph = build_graph(llm=llm, retriever=retriever or _StubRetriever(),
                        java_api_module=java_api_module, checkpointer=checkpointer)
    monkeypatch.setattr(main_mod, "get_graph", lambda: graph)
    return TestClient(main_mod.app)


def _read_sse(client, payload: dict) -> str:
    """POST /v1/chat/stream 并收集全部 SSE 文本"""
    os.environ["ALLOW_ANON_CHAT"] = "1"  # 测试放行匿名（_check_service_key 读请求时环境变量）
    try:
        with client.stream("POST", "/v1/chat/stream", json=payload) as resp:
            assert resp.status_code == 200
            assert resp.headers["content-type"].startswith("text/event-stream")
            return "".join(resp.iter_text())
    finally:
        os.environ.pop("ALLOW_ANON_CHAT", None)


def _parse_events(raw: str) -> list[tuple[str, dict]]:
    """把 SSE 文本解析为 (event, data) 列表"""
    events = []
    for block in raw.strip().split("\n\n"):
        event, data = "message", ""
        for line in block.splitlines():
            if line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data += line[len("data:"):].strip()
        try:
            events.append((event, json.loads(data)))
        except json.JSONDecodeError:
            events.append((event, {"_raw": data}))
    return events


def test_stream_tokens_status_and_done(monkeypatch):
    """正常路径：token 逐字下发；status 先给规则层标签再给本轮调用的工具；done 带建议问题。

    规划器那两步的输出（tool_calls 与收敛用的空内容）都不得变成用户可见 token——
    智能体改造后这是最容易外泄的一段中间过程。
    """
    llm = AgentFakeChatModel(messages=iter([
        _kb_plan("运费怎么算"),         # agent 第一步：选择工具（取到资料即退出，不再规划）
        AIMessage(content=REPLY_TEXT),  # generate 节点（token 下发）
    ]))
    client = _client(monkeypatch, llm)

    raw = _read_sse(client, {"sessionId": "t_stream", "message": "运费怎么算", "userId": None})
    events = _parse_events(raw)
    names = [e for e, _ in events]
    assert "status" in names and "done" in names

    token_text = "".join(d.get("content", "") for e, d in events if e == "token")
    assert REPLY_TEXT in token_text
    assert "tool_calls" not in token_text and "kb_search" not in token_text  # 内部编排不外显

    # 规则前置没判出高置信标签 → intent 为空；"要办哪件事"由 agent 事件的 tools 表示
    intent_status = next(d for e, d in events if e == "status" and d.get("stage") == "intent")
    assert intent_status["intent"] in ("", None)
    agent_status = next(d for e, d in events if e == "status" and d.get("stage") == "agent")
    assert agent_status["tools"] == ["kb_search"]

    done = next(d for e, d in events if e == "done")
    assert done["sessionId"] == "t_stream"
    assert isinstance(done.get("suggestions"), list) and done["suggestions"]


def test_stream_fallback_when_llm_fails(monkeypatch):
    """降级路径：生成节点 LLM 抛异常 → 降级话术经最终状态兜底一次性下发"""

    class FailingLLM:
        def invoke(self, messages):
            for m in messages:
                if isinstance(m, SystemMessage) and "意图识别器" in m.content:
                    return AIMessage(content=INTENT_SMALLTALK)
            raise RuntimeError("Agnes 429 限流（模拟）")

        def bind_tools(self, tools, **kwargs):
            raise NotImplementedError  # 不支持 function calling → 走"分类 + 单工具"降级

    # 需要带 checkpointer：兜底经 aget_state 读最终状态
    client = _client(monkeypatch, FailingLLM(), checkpointer=MemorySaver())

    raw = _read_sse(client, {"sessionId": "t_fallback", "message": "你好", "userId": None})
    events = _parse_events(raw)

    token_text = "".join(d.get("content", "") for e, d in events if e == "token")
    assert token_text.strip(), "降级场景下气泡不应为空"
    assert [e for e, _ in events][-1] == "done"


def test_thread_id_namespaced_by_trusted_user(monkeypatch):
    """同一 sessionId 落在不同用户名下必须是两个互不可见的 thread。

    sessionId 由客户端携带，thread key 若不含服务端可信 userId，旁人带上别人的
    会话号就能接走对方的多轮上下文（其中可能已有订单号、金额、售后状态）。
    """
    import app.main as main_mod
    from app.schemas.chat import ChatRequest

    assert main_mod._thread_id(ChatRequest(sessionId="s1", message="m")) == "anon:s1"
    assert main_mod._thread_id(ChatRequest(sessionId="s1", message="m", userId=7)) == "7:s1"

    def turn():
        return [_kb_plan("运费怎么算"), AIMessage(content=REPLY_TEXT)]

    graph = build_graph(
        llm=AgentFakeChatModel(messages=iter(turn() + turn())),
        retriever=_StubRetriever(),
        checkpointer=MemorySaver(),
    )
    monkeypatch.setattr(main_mod, "get_graph", lambda: graph)
    client = TestClient(main_mod.app)

    _read_sse(client, {"sessionId": "shared", "message": "运费怎么算", "userId": 7})
    _read_sse(client, {"sessionId": "shared", "message": "运费怎么算", "userId": 8})

    def _humans(thread_id: str) -> int:
        snapshot = graph.get_state({"configurable": {"thread_id": thread_id}})
        return sum(1 for m in (snapshot.values or {}).get("messages", []) if isinstance(m, HumanMessage))

    assert _humans("7:shared") == 1  # 各自只有本轮，未吸入对方的历史
    assert _humans("8:shared") == 1


def test_retriever_outage_degrades_friendly_inside_graph(monkeypatch):
    """检索器炸了不该把流打断：图内按"取数故障"出口给友好话术，内部细节一个字样都不外泄。

    红线：依赖故障必须静默降级，用户端不出现"系统错误"这类兜底文案，也不出现集合名/内网地址。
    """

    class BoomRetriever:
        def retrieve(self, question, top_k=5):
            raise RuntimeError("chroma 连接失败 http://127.0.0.1:8000/bge-large-zh 429")

    llm = AgentFakeChatModel(messages=iter([
        _kb_plan("运费怎么算"),   # 规划 → 观察 status=error → 允许改写重试一次
        AIMessage(content=""),    # 第二次规划不再调用工具 → 结束（作答走确定性降级话术，不调 LLM）
    ]))
    client = _client(monkeypatch, llm, checkpointer=MemorySaver(), retriever=BoomRetriever())

    raw = _read_sse(client, {"sessionId": "t_outage", "message": "运费怎么算", "userId": None})
    events = _parse_events(raw)
    token_text = "".join(d.get("content", "") for e, d in events if e == "token")
    assert "400-000-0000" in token_text and "繁忙" in token_text
    for leak in ("chroma", "127.0.0.1", "429", "bge-large", "系统错误"):
        assert leak not in raw, f"内部细节泄漏到用户端：{leak}"
    assert [e for e, _ in events][-1] == "done"


def test_stream_error_frame_carries_only_code(monkeypatch):
    """error 事件只发 code：异常文本（模型/网关/内部地址）绝不进用户端。

    这里直接让图本身抛异常（真正的意外故障），验证最后一道防线：
    客户端按 code 查白名单文案，细节只进服务端日志。
    """
    import app.main as main_mod

    class BoomGraph:
        async def astream(self, *args, **kwargs):
            raise RuntimeError("Agnes 网关 429：https://internal-gateway/agnes/chat/completions")
            yield  # noqa: RET503 - 让本函数成为异步生成器（graph.astream 的调用形态）

    monkeypatch.setattr(main_mod, "get_graph", lambda: BoomGraph())
    client = TestClient(main_mod.app)

    raw = _read_sse(client, {"sessionId": "t_err", "message": "运费怎么算", "userId": None})
    events = _parse_events(raw)

    err = next(d for e, d in events if e == "error")
    assert err["code"] == "ai_upstream"
    for leak in ("internal-gateway", "127.0.0.1", "429", "agnes"):
        assert leak not in raw, f"内部细节泄漏到用户端：{leak}"


# ---------- 结构化商品卡：items 事件与 done.items ----------
class _RecommendApi:
    def __init__(self):
        self.search_calls = []

    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        self.search_calls.append({"keyword": keyword, "ids": ids})
        return list(_CARD)

    def fetch_product(self, product_id):
        return None

    def format_payload(self, data):
        return json.dumps(data, ensure_ascii=False)


def _recommend_client(monkeypatch, api, messages):
    import app.main as main_mod

    graph = build_graph(
        llm=AgentFakeChatModel(messages=iter(messages)),
        retriever=_StubRetriever(), java_api_module=api, checkpointer=MemorySaver(),
    )
    monkeypatch.setattr(main_mod, "get_graph", lambda: graph)
    return TestClient(main_mod.app)


def test_stream_emits_items_event_and_done_payload(monkeypatch):
    """推荐命中时：候选一确定就下发 items 事件（卡片不必等 LLM 说完），done 也带一份兜底。"""
    api = _RecommendApi()
    client = _recommend_client(monkeypatch, api, [
        _rec_plan("有什么水果推荐", keyword="水果", inDomain=True),
        AIMessage(content="给你挑了赣南脐橙"),
    ])

    raw = _read_sse(client, {"sessionId": "t_items", "message": "有什么水果推荐", "userId": None})
    events = _parse_events(raw)

    items_events = [d for e, d in events if e == "items"]
    assert items_events, "应有一条 items 事件"
    assert items_events[0]["items"][0]["name"] == "赣南脐橙"
    assert items_events[0]["items"][0]["mainImage"] == "https://img/2.jpg"

    done = next(d for e, d in events if e == "done")
    assert done["items"][0]["id"] == 2, "done 也带一份，前端只监听 done 也能渲染卡片"

    # 卡片应在回复说完之前就到位：items 事件位置早于最后一个 token
    names = [e for e, _ in events]
    last_token_idx = max(i for i, e in enumerate(names) if e == "token")
    assert names.index("items") < last_token_idx
    assert api.search_calls[0]["keyword"] == "水果"  # 模型给的品类词真的进了 Java 查询


def test_sync_chat_returns_items(monkeypatch):
    """同步接口同口径：非流式调用也要拿到 items，否则两条路会给出不同能力的回复。"""
    api = _RecommendApi()
    client = _recommend_client(monkeypatch, api, [
        _rec_plan("有什么水果推荐", keyword="水果", inDomain=True),
        AIMessage(content="给你挑了赣南脐橙"),
    ])
    os.environ["ALLOW_ANON_CHAT"] = "1"
    try:
        resp = client.post("/v1/chat", json={"sessionId": "t_sync", "message": "有什么水果推荐"})
        assert resp.status_code == 200
        body = resp.json()
    finally:
        os.environ.pop("ALLOW_ANON_CHAT", None)
    assert body["items"][0]["name"] == "赣南脐橙"
    assert "赣南脐橙" in body["reply"]


def test_stream_no_items_for_non_recommend(monkeypatch):
    """FAQ 分支不该出现 items 事件：卡片只属于导购结果，混进去会误导用户。"""
    llm = AgentFakeChatModel(messages=iter([
        _kb_plan("运费怎么算"), AIMessage(content=REPLY_TEXT),
    ]))
    client = _client(monkeypatch, llm)
    raw = _read_sse(client, {"sessionId": "t_faq_no_items", "message": "运费怎么算"})
    names = [e for e, _ in _parse_events(raw)]
    assert "items" not in names
