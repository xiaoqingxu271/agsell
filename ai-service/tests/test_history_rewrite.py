"""历史感知召回查询改写测试（多轮丢约束的机制化兜底）

背景：规划器能看到最近 6 轮历史，通常会自己把"便宜点的"补全成完整检索句；
但工具参数缺省时执行器只能拿到最后一条消息——上一轮的品类与预算就丢了
（评估清单 1.3 的遗留项）。build_contextual_query 用零成本规则兜底：
短追问/带指代特征的消息自动携带上一条用户消息；独立完整问句原样返回。
"""
from langchain_core.messages import AIMessage, HumanMessage

from app.agent.executors import build_contextual_query


def _state(*contents):
    msgs = []
    for c in contents:
        role = AIMessage if c.startswith("ai:") else HumanMessage
        msgs.append(role(content=c[3:] if c.startswith("ai:") else c))
    return {"messages": msgs}


def test_first_turn_returns_as_is():
    assert build_contextual_query(_state("有什么水果推荐")) == "有什么水果推荐"


def test_short_followup_inherits_previous_topic():
    """「有没有便宜点的」承接上一轮的水果诉求 → 召回串带上文"""
    q = build_contextual_query(_state("有什么水果推荐", "ai:推荐了三款", "有没有便宜点的"))
    assert q == "有什么水果推荐 有没有便宜点的"


def test_anaphora_marker_inherits_even_for_longer_query():
    q = build_contextual_query(_state("赣南脐橙怎么样", "ai:介绍了脐橙", "这个和其他的比有什么区别吗"))
    assert q.startswith("赣南脐橙怎么样 ")


def test_independent_long_query_not_polluted():
    """新一轮完整独立问句（超短追问阈值且无指代特征）不应被上一轮话题污染"""
    q = build_contextual_query(_state("有什么水果推荐", "ai:推荐了三款", "你们家江西赣南的脐橙什么时候发货"))
    assert q == "你们家江西赣南的脐橙什么时候发货"


def test_no_history_returns_empty():
    assert build_contextual_query({"messages": []}) == ""
    assert build_contextual_query({}) == ""


def test_previous_topic_truncated_and_result_capped():
    long_prev = "一" * 80
    q = build_contextual_query(_state(long_prev, "ai:好的", "便宜点的"))
    assert len(q) <= 100
    assert q.startswith("一")
