"""阶段七：多轮历史窗口截断（trim_messages）

验收点：
1. system 提示词始终保留在最前；
2. 超预算时只保留最近对话，且截断后从一条 HumanMessage 开始（无半截轮次）；
3. 当前用户问题（最后一条 HumanMessage）永远不被裁掉；
4. max_chars<=0 或预算足够大时不裁剪。
"""
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.agent.nodes import _build_messages


def test_trim_keeps_system_and_latest_question():
    history = []
    for i in range(20):
        history.append(HumanMessage(content=f"问题{i}_" + "x" * 20))
        history.append(AIMessage(content=f"回答{i}_" + "y" * 20))
    history.append(HumanMessage(content="当前问题"))  # 本轮问题，必须保留

    msgs = _build_messages("系统提示词", history, max_chars=120)

    assert isinstance(msgs[0], SystemMessage)
    assert msgs[0].content == "系统提示词"
    assert len(msgs) < len(history) + 1            # 历史被裁剪
    assert isinstance(msgs[1], HumanMessage)        # system 后第一条是完整 human 轮次
    assert isinstance(msgs[-1], HumanMessage)
    assert msgs[-1].content == "当前问题"            # 本轮问题不被裁掉


def test_trim_respects_char_budget():
    history = []
    for _ in range(10):
        history.append(HumanMessage(content="h" * 10))
        history.append(AIMessage(content="a" * 10))
    history.append(HumanMessage(content="当前"))

    msgs = _build_messages("s", history, max_chars=50)
    body_total = sum(len(m.content) for m in msgs if not isinstance(m, SystemMessage))
    assert body_total <= 50
    assert isinstance(msgs[1], HumanMessage)
    assert msgs[-1].content == "当前"


def test_no_trim_when_disabled_or_budget_large():
    history = [HumanMessage(content="你好"), AIMessage(content="您好，请问需要什么帮助")]
    assert len(_build_messages("sys", history, max_chars=0)) == 3
    assert len(_build_messages("sys", history, max_chars=100000)) == 3
