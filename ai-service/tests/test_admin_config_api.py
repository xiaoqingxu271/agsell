"""管理端配置端点契约测试：钉死 Java↔Python 的请求体形状与鉴权（防 P0-1 回归）

背景：Java updateConfig 曾把平铺 {"ai_kb_top_k": "5"} 直接 PUT 给 Python，
而 Python 契约是 {"values": {...}}——pydantic 静默忽略多余字段后 set_values({}) 返回 []，
HTTP 200 + 未变化的旧快照，调参功能上线即废且无任何报错。这组测试保证：
1. {"values": {...}} 形状能正确落到 set_values（并只把白名单值传下去）；
2. 平铺形状（老 bug 的请求体）收到的是空更新——把它钉成预期行为，防止有人"顺手改回"；
3. 两个端点都要求 X-Internal-Key（fail-closed）。
"""
import pytest
from fastapi.testclient import TestClient

from app import runtime_config


@pytest.fixture()
def client(monkeypatch):
    # 不打真库：snapshot/set_values 全部替身，只验证端点的形状与流转
    monkeypatch.setattr(runtime_config, "snapshot", lambda: [
        {"key": "ai_kb_top_k", "description": "知识库检索返回条数", "type": "int",
         "value": 5, "default": 5, "overridden": False, "min": 1, "max": 20},
    ])
    from app.main import app

    return TestClient(app)


HEADERS = {"X-Internal-Key": "test-internal-key"}


def test_put_config_with_wrapped_values_reaches_set_values(client, monkeypatch):
    captured = {}

    def fake_set_values(values):
        captured.update(values)
        runtime_config.invalidate()
        return list(values)

    monkeypatch.setattr(runtime_config, "set_values", fake_set_values)
    resp = client.put("/v1/admin/kb/config", json={"values": {"ai_kb_top_k": "8"}}, headers=HEADERS)
    assert resp.status_code == 200
    assert captured == {"ai_kb_top_k": "8"}


def test_put_config_flat_body_is_silently_empty(client, monkeypatch):
    # 平铺形状 = Java 侧旧 bug 的请求体：不炸、也不写任何键。
    # 若 Java 回归成平铺发送，这里 captured 为空 + 快照不变就是它的症状
    captured = {}
    monkeypatch.setattr(runtime_config, "set_values", lambda v: captured.update(v) or list(v))
    resp = client.put("/v1/admin/kb/config", json={"ai_kb_top_k": "8"}, headers=HEADERS)
    assert resp.status_code == 200
    assert captured == {}


def test_put_config_requires_internal_key(client):
    assert client.put("/v1/admin/kb/config", json={"values": {}}).status_code in (401, 403)


def test_get_config_requires_internal_key(client):
    assert client.get("/v1/admin/kb/config").status_code in (401, 403)


def test_get_config_returns_snapshot(client):
    resp = client.get("/v1/admin/kb/config", headers=HEADERS)
    assert resp.status_code == 200
    items = resp.json()["items"]
    assert items[0]["key"] == "ai_kb_top_k"
