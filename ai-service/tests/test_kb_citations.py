"""知识库引用结构化下发测试（反幻觉的证据链可视化）

引用由服务端按检索命中拼装（id+问句），SSE done / 同步响应结构化透传，
前端渲染"依据"标签——不经模型复述，模型自己列引用必然漂移。
"""
from app.agent.executors import ToolContext, exec_kb_search, run_tool
from app.agent.nodes import _collapse_observations


class FakeRetriever:
    def __init__(self, hits):
        self._hits = hits

    def retrieve(self, question):
        return self._hits


def _ctx(retriever):
    return ToolContext(retriever=retriever)


def test_kb_search_returns_references_from_top_hits():
    hits = [
        {"id": 5, "question": "运费怎么算？", "answer": "满 99 包邮", "score": 0.9},
        {"id": 6, "question": "几天发货？", "answer": "48 小时内", "score": 0.8},
        {"id": 7, "question": "能退货吗？", "answer": "七天无理由", "score": 0.7},
        {"id": 8, "question": "第四条", "answer": "x", "score": 0.6},
    ]
    obs = exec_kb_search(_ctx(FakeRetriever(hits)), {"messages": []}, {})
    assert obs.status == "ok"
    # 最多带 3 条引用（与卡片 REC_CARD_LIMIT 的"少而准"同一取向）
    assert [r["id"] for r in obs.references] == [5, 6, 7]
    assert all(set(r.keys()) == {"id", "question"} for r in obs.references)


def test_empty_hits_carry_no_references():
    obs = exec_kb_search(_ctx(FakeRetriever([])), {"messages": []}, {})
    assert obs.status == "empty"
    assert obs.references == []


def test_collapse_dedupes_references_across_tool_calls():
    """同轮多次 kb_search（反思重试/多意图）引用去重合并，整轮上限 4 条"""
    o1 = exec_kb_search(_ctx(FakeRetriever([
        {"id": 5, "question": "运费怎么算？", "answer": "a", "score": 0.9},
        {"id": 6, "question": "几天发货？", "answer": "b", "score": 0.8},
    ])), {"messages": []}, {})
    o2 = exec_kb_search(_ctx(FakeRetriever([
        {"id": 6, "question": "几天发货？", "answer": "b", "score": 0.9},
        {"id": 7, "question": "能退货吗？", "answer": "c", "score": 0.8},
        {"id": 8, "question": "第四条", "answer": "d", "score": 0.7},
        {"id": 9, "question": "第五条", "answer": "e", "score": 0.6},
    ])), {"messages": []}, {})
    state = _collapse_observations([o1, o2])
    ids = [r["id"] for r in state["references"]]
    assert ids == [5, 6, 7, 8]  # 去重保序，第 5 条被上限截断


def test_run_tool_preserves_references():
    obs = run_tool(_ctx(FakeRetriever([
        {"id": 5, "question": "运费怎么算？", "answer": "a", "score": 0.9},
    ])), {"messages": []}, "kb_search", {"question": "运费"})
    # run_tool 统一入口后 references 不丢（as_dict 序列化给生成节点与日志）
    assert obs.as_dict()["references"] == [{"id": 5, "question": "运费怎么算？"}]
