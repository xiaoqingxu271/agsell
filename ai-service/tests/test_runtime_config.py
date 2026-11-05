"""运行时配置测试：归一/夹取/兜底回落（不打真库，_load 一律 monkeypatch）"""
import pytest

from app import runtime_config
from app.config import settings


@pytest.fixture(autouse=True)
def _reset_cache():
    runtime_config.invalidate()
    yield
    runtime_config.invalidate()


# ---------- effective：覆盖优先，缺/脏回落默认 ----------

def test_effective_falls_back_to_default_when_no_override(monkeypatch):
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {})
    assert runtime_config.effective("ai_kb_top_k") == settings.KB_TOP_K
    assert runtime_config.effective("ai_rerank_enabled") == settings.RERANK_ENABLED


def test_effective_uses_override_and_clamps(monkeypatch):
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {"ai_kb_top_k": "8"})
    assert runtime_config.effective("ai_kb_top_k") == 8
    # 越界夹取：max=20
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {"ai_kb_top_k": "999"})
    assert runtime_config.effective("ai_kb_top_k") == 20
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {"ai_kb_top_k": "0"})
    assert runtime_config.effective("ai_kb_top_k") == 1


def test_effective_dirty_value_falls_back(monkeypatch):
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {"ai_kb_top_k": "abc"})
    assert runtime_config.effective("ai_kb_top_k") == settings.KB_TOP_K


def test_effective_bool_parses_common_forms(monkeypatch):
    for raw, expected in [("true", True), ("1", True), ("ON", True), ("false", False), ("0", False)]:
        monkeypatch.setattr(runtime_config, "_load", lambda force=False, r=raw: {"ai_rerank_enabled": r})
        assert runtime_config.effective("ai_rerank_enabled") is expected


def test_effective_unknown_key_raises():
    with pytest.raises(KeyError):
        runtime_config.effective("ai_not_exists")


# ---------- _normalize：PUT 前的归一 ----------

def test_normalize_bool_variants():
    assert runtime_config._normalize("ai_rerank_enabled", "开启") == "true"
    assert runtime_config._normalize("ai_rerank_enabled", "false") == "false"
    with pytest.raises(ValueError):
        runtime_config._normalize("ai_rerank_enabled", "maybe")


def test_normalize_int_clamps_and_rejects_garbage():
    assert runtime_config._normalize("ai_agent_max_steps", "3") == "3"
    assert runtime_config._normalize("ai_agent_max_steps", "99") == "6"   # 夹到 max
    with pytest.raises(ValueError):
        runtime_config._normalize("ai_agent_max_steps", "很多")


def test_normalize_rejects_empty():
    with pytest.raises(ValueError):
        runtime_config._normalize("ai_kb_top_k", "  ")


# ---------- set_values：白名单外静默忽略 ----------

def test_set_values_ignores_non_whitelisted(monkeypatch):
    called = {}
    monkeypatch.setattr(runtime_config, "_normalize", lambda k, r: r.strip())

    class _FakeConn:
        def __init__(self, store):
            self._store = store

        def execute(self, stmt, params=None):
            called[params["k"]] = params["v"]

    class _FakeEngineCtx:
        def __init__(self):
            self.conn = _FakeConn(called)

        def __enter__(self):
            return self.conn

        def __exit__(self, *a):
            return False

    monkeypatch.setattr(runtime_config, "engine", type("E", (), {"begin": lambda self: _FakeEngineCtx()})())
    updated = runtime_config.set_values({"ai_kb_top_k": "6", "not_ai": "x"})
    runtime_config.invalidate()
    assert updated == ["ai_kb_top_k"]
    assert called == {"ai_kb_top_k": "6"}
    # invalidate 后下次读会重新 load（这里 _load 仍被 mock，不验证 DB 回读）


# ---------- snapshot：管理端展示结构 ----------

def test_snapshot_shapes_all_fields(monkeypatch):
    monkeypatch.setattr(runtime_config, "_load", lambda force=False: {"ai_kb_top_k": "8"})
    items = {i["key"]: i for i in runtime_config.snapshot()}
    assert set(items) == set(runtime_config.FIELDS)
    assert items["ai_kb_top_k"]["value"] == 8
    assert items["ai_kb_top_k"]["overridden"] is True
    assert items["ai_kb_top_k"]["default"] == settings.KB_TOP_K
    # 未覆盖的键 overridden=False
    assert items["ai_rerank_enabled"]["overridden"] is False
