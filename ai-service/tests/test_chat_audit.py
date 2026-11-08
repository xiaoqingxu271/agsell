"""对话审计落库测试：record_turn 的完整链路（建表+插行+查询）与旁路铁律

E2E 教训：chat_audit 此前没有测试，漏 import Base 后 NameError 被 except 吞掉，
审计静默丢行、线上零告警。旁路模块"失败不影响业务"的设计反而掩盖了"永远在失败"——
本测试用真实引擎直连（DATABASE_URL 指向的库），把整条链路钉死。
"""
import pytest

from app import chat_audit


def test_record_turn_persists_row_and_is_queryable():
    """真实落库一轮并查回：字段对位、截断生效、旁路不抛"""
    chat_audit._ensure_table()
    long_reply = "很" * 20000  # 超过 16000 截断阈值的回复
    chat_audit.record_turn(
        user_id=9981,
        session_id="pytest-audit-001",
        question="运费怎么算？",
        reply=long_reply,
        intent="",
        tools=["kb_search", "order_query"],
        refused=False,
        hits=2,
        refs=[{"id": 19, "question": "运费怎么计算？"}],
        duration_ms=1234,
    )
    from sqlalchemy import text

    from app.db import engine

    with engine.connect() as conn:
        row = conn.execute(text(
            "select user_id, session_id, question, char_length(reply), length(reply), tools, refused, hits, refs, duration_ms "
            "from ai_chat_log where session_id='pytest-audit-001' order by id desc limit 1"
        )).one()
    assert row.user_id == 9981
    assert row.session_id == "pytest-audit-001"
    assert row.question == "运费怎么算？"
    assert row[3] == 16000          # char_length：截断到 16000 字符
    assert row[4] <= 65535          # 字节长度落在 TEXT 列上限内（utf8mb4 中文 3 字节/字符）
    assert row.tools == "kb_search,order_query"
    assert row.refused == 0
    assert row.hits == 2
    assert row.refs == "19"
    assert row.duration_ms == 1234


def test_record_turn_never_raises():
    """旁路铁律：任何坏输入都不允许把异常抛回调用方（对话主链路）"""
    chat_audit._ensure_table()
    # 全 None/脏值：不应该抛
    chat_audit.record_turn(None, "", "", "", tools=None, refs=None, duration_ms=None)
    chat_audit.record_turn(1, "s" * 200, "q", "r", refs=[{"no_id": 1}], duration_ms=-5)


@pytest.fixture(autouse=True)
def _cleanup():
    yield
    from sqlalchemy import text

    from app.db import engine

    with engine.begin() as conn:
        conn.execute(text("delete from ai_chat_log where session_id like 'pytest-audit-%' or session_id = ''"))
