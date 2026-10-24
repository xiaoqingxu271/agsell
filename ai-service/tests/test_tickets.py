"""转人工工单闭环（第一批整改）

背景：escalate_human 此前只回一句电话话术，不落任何记录，管理端无从跟进。
现在转人工触发后回调 Java POST /ai/internal/ticket 落一张 ai_ticket，
管理后台「AI 工单」页处理。本文件钉住两件事：
1. 工单内容提取——只有真的转人工才落单，原因优先取工具入参，规则短路路径给中性兜底；
2. 落库尽力而为——回调失败只记日志，绝不让工单丢失拖垮已经生成的回复。
"""
import json

from app.tools import tickets
from app.tools.java_api import JavaApiError, create_human_ticket


# ---------- 内容提取 ----------

def test_no_ticket_when_human_not_triggered():
    """普通 FAQ/导购/订单轮次绝不落工单"""
    assert tickets.ticket_payload_from_state("运费怎么算", 1, "s1", [
        {"tool": "kb_search", "kind": "kb", "status": "ok"},
    ], human_flag=False) is None
    assert tickets.ticket_payload_from_state("运费怎么算", 1, "s1", [], False) is None


def test_reason_taken_from_escalate_tool_args():
    obs = [{"tool": "escalate_human", "kind": "human", "status": "ok",
            "args": {"reason": "用户情绪激烈要求投诉"}}]
    payload = tickets.ticket_payload_from_state("我要投诉！", 7, "s2", obs, human_flag=True)
    assert payload == {
        "userId": 7,
        "sessionId": "s2",
        "question": "我要投诉！",
        "reason": "用户情绪激烈要求投诉",
    }


def test_rule_short_circuit_gets_neutral_reason():
    """human_fallback 节点直出时没有观察，用中性文案兜底而不是空串"""
    payload = tickets.ticket_payload_from_state("转人工", None, "s3", [], human_flag=True)
    assert payload["userId"] is None  # 游客转人工同样留痕
    assert payload["reason"] == "用户明确要求人工跟进"


def test_long_question_and_reason_are_truncated():
    obs = [{"kind": "human", "args": {"reason": "r" * 400}}]
    payload = tickets.ticket_payload_from_state("q" * 800, 1, "s4", obs, human_flag=True)
    assert len(payload["question"]) <= 500
    assert len(payload["reason"]) <= 255


def test_first_human_observation_with_reason_wins():
    """多个 human 观察时取第一个带原因的；全空则兜底"""
    obs = [
        {"kind": "human", "args": {}},
        {"kind": "human", "args": {"reason": "超能力范围"}},
    ]
    payload = tickets.ticket_payload_from_state("q", 1, "s5", obs, human_flag=True)
    assert payload["reason"] == "超能力范围"


# ---------- Java 回调客户端 ----------

def test_create_ticket_posts_expected_payload(monkeypatch):
    captured = {}

    def fake_post(path, payload):
        captured["path"] = path
        captured["payload"] = payload
        return {"id": 1}

    monkeypatch.setattr("app.tools.java_api._post", fake_post)
    assert create_human_ticket(9, "sess_1", "  帮我投诉  ", "质量问题") is True
    assert captured["path"] == "/ai/internal/ticket"
    body = captured["payload"]
    assert body["userId"] == 9
    assert body["sessionId"] == "sess_1"
    assert body["question"] == "帮我投诉"  # trim
    assert body["reason"] == "质量问题"


def test_create_ticket_guest_omits_user_id(monkeypatch):
    captured = {}
    monkeypatch.setattr("app.tools.java_api._post",
                        lambda path, payload: captured.update(payload=payload) or {})
    assert create_human_ticket(None, "sess_2", "人工", "") is True
    assert "userId" not in captured["payload"]
    assert captured["payload"]["reason"] is None


def test_create_ticket_swallows_java_failure(monkeypatch):
    def boom(path, payload):
        raise JavaApiError("无法连接 Java 主系统")

    monkeypatch.setattr("app.tools.java_api._post", boom)
    assert create_human_ticket(1, "s", "q", "r") is False


# ---------- fire / fire_async ----------

def test_fire_is_noop_for_none_payload(monkeypatch):
    called = []
    monkeypatch.setattr(tickets.java_api, "create_human_ticket",
                        lambda *a, **k: called.append(a) or True)
    tickets.fire(None)
    assert called == []


def test_fire_swallows_unexpected_exceptions(monkeypatch):
    def boom(*a, **k):
        raise RuntimeError("redis down")

    monkeypatch.setattr(tickets.java_api, "create_human_ticket", boom)
    tickets.fire({"userId": 1, "sessionId": "s", "question": "q", "reason": "r"})  # 不应抛出


def test_fire_async_runs_without_running_loop(monkeypatch):
    """同步上下文（如脚本）退化为直接执行"""
    called = []
    monkeypatch.setattr(tickets, "fire", lambda p: called.append(p))
    payload = {"userId": 1, "sessionId": "s", "question": "q", "reason": "r"}
    tickets.fire_async(payload)
    assert called == [payload]
