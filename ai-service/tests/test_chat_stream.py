"""SSE 流式对话接口测试（阶段六）：stub LLM，不依赖网络与 API Key

覆盖：
1. 正常流式：token 逐字下发 + status 阶段事件 + done 收尾（建议问题），
   且 intent 节点内部的 LLM 分类输出（JSON）不得泄漏到 token 流；
2. 无 token 兜底：生成节点抛异常（模拟 Agnes 限流）→ 降级话术经
   最终状态一次性下发，气泡不为空。
"""
import json
import os

from fastapi.testclient import TestClient
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, SystemMessage
from langgraph.checkpoint.memory import MemorySaver

from app.agent.graph import build_graph

INTENT_SMALLTALK = json.dumps({"intent": "smalltalk", "order_no": None}, ensure_ascii=False)
INTENT_FAQ = json.dumps({"intent": "faq", "order_no": None}, ensure_ascii=False)
REPLY_TEXT = "您好！很高兴为您服务，请问有什么可以帮您？"


class _StubRetriever:
    def retrieve(self, question, top_k=5):
        return [{"id": 1, "question": "运费怎么算", "answer": "按重量计费，满额包邮", "score": 0.9}]


def _client(monkeypatch, llm, checkpointer=None, retriever=None):
    import app.main as main_mod

    graph = build_graph(llm=llm, retriever=retriever or _StubRetriever(), checkpointer=checkpointer)
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


def test_stream_tokens_and_done(monkeypatch):
    """正常路径：意图分类消耗第 1 条 LLM 消息（其 token 必须被过滤），generate 回复逐字下发，done 带建议问题。

    用走 LLM 意图分类的 faq 输入（"运费怎么算"不被高置信规则前置），才能覆盖 intent 节点
    内部 LLM 输出不泄漏到 token 流的过滤逻辑；纯寒暄输入的意图已由规则前置判定、不调 LLM。
    """
    llm = GenericFakeChatModel(messages=iter([
        AIMessage(content=INTENT_FAQ),       # intent 节点（token 必须被过滤）
        AIMessage(content=REPLY_TEXT),       # generate 节点（token 下发）
    ]))
    client = _client(monkeypatch, llm)

    raw = _read_sse(client, {"sessionId": "t_stream", "message": "运费怎么算", "userId": None})
    events = _parse_events(raw)

    names = [e for e, _ in events]
    assert "status" in names and "done" in names

    # token 事件拼起来应是完整回复，且不含意图分类 JSON
    token_text = "".join(d.get("content", "") for e, d in events if e == "token")
    assert REPLY_TEXT in token_text
    assert "intent" not in token_text

    # status 有 intent 阶段事件；done 带建议问题
    intent_status = next(d for e, d in events if e == "status" and d.get("stage") == "intent")
    assert intent_status["intent"] == "faq"
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

    # 需要带 checkpointer：兜底经 aget_state 读最终状态
    llm = FailingLLM()
    client = _client(monkeypatch, llm, checkpointer=MemorySaver())

    raw = _read_sse(client, {"sessionId": "t_fallback", "message": "你好", "userId": None})
    events = _parse_events(raw)

    token_text = "".join(d.get("content", "") for e, d in events if e == "token")
    assert token_text.strip(), "降级场景下气泡不应为空"
    assert [e for e, _ in events][-1] == "done"
