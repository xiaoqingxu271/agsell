"""溯源切块测试：HTML 剥离、句子边界切分、条目构造（假数据行，不打真库）"""
from app.rag.chunker import build_trace_entries, split_chunks, strip_html


class _Result:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class _FakeSession:
    """按调用顺序吐出预置结果（生产记录 → 溯源档案）"""

    def __init__(self, *responses):
        self._responses = list(responses)

    def execute(self, stmt, params=None):
        return _Result(self._responses.pop(0))


def test_strip_html_removes_tags_and_entities():
    html = '<p>【赣南脐橙】产地直发。</p><img src="https://x/y.jpg" style="width:100%" /><p>坏果包赔&nbsp;支持溯源</p>'
    assert strip_html(html) == "【赣南脐橙】产地直发。 坏果包赔 支持溯源"
    assert strip_html(None) == ""
    assert strip_html("<p></p>") == ""


def test_split_chunks_short_text_single_block():
    assert split_chunks("一句话的记录") == ["一句话的记录"]
    assert split_chunks("") == []


def test_split_chunks_respects_sentence_boundaries():
    text = "。" .join(["第一句内容" * 5] * 4) + "。"
    chunks = split_chunks(text, size=60, overlap=10)
    assert len(chunks) >= 2
    # 每块不超过 size（单句超长才允许硬切）
    assert all(len(c) <= 60 for c in chunks)
    # 内容不丢：全部句子拼回去覆盖原文的句子集合
    assert all(s in text for c in chunks for s in c.split("。") if s)


def test_split_chunks_hard_splits_overlong_sentence():
    long_sentence = "超" * 500
    chunks = split_chunks(long_sentence, size=180, overlap=30)
    assert len(chunks) >= 3
    assert all(len(c) <= 180 for c in chunks)


def test_build_trace_entries_shapes_production_and_traceability():
    prod_rows = [(
        1, "HARVEST", "2026-09-20", "清晨采收，当天预冷。", "B2026-09",
        "赣南脐橙 新鲜当季", "江西赣州",
    )]
    trace_rows = [(
        9, "B2026-09", "张三", "江西省", "赣州市", "2026-09-21",
        "农残检测合格", "绿色食品", "赣南脐橙 新鲜当季",
    )]
    entries = build_trace_entries(_FakeSession(prod_rows, trace_rows))

    assert len(entries) == 2  # 一条生产记录 + 一个溯源档案段
    rec, trace = entries
    assert rec["question"] == "赣南脐橙 新鲜当季的采收记录"
    assert "采收" in rec["answer"] and "2026-09-20" in rec["answer"]
    assert rec["category"] == "trace_knowledge"
    assert "赣南脐橙" in rec["keywords"]

    assert trace["question"] == "赣南脐橙 新鲜当季的溯源档案"
    assert "批次 B2026-09" in trace["answer"] and "张三" in trace["answer"]
    assert "溯源" in trace["keywords"]
