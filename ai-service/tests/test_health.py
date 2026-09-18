"""健康检查接口测试（不依赖网络与 API Key）"""
import os

from fastapi.testclient import TestClient

os.environ.setdefault("ALLOW_ANON_CHAT", "1")  # 本地测试放行匿名 chat，避免依赖服务密钥

from app.main import app  # noqa: E402

client = TestClient(app)


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["model"] == "agnes-2.5-flash"
    assert data["version"] == "0.1.0"


def test_chat_rejects_missing_service_key():
    """阶段五：无 X-Internal-Key 调用 /v1/chat → 401（服务间鉴权）"""
    # 注意：ALLOW_ANON_CHAT 默认关闭时才拒绝；此处显式确认配置默认值
    import app.main as main_mod

    os.environ.pop("ALLOW_ANON_CHAT", None)  # 模拟生产默认（未放行）
    try:
        main_mod._check_service_key(None)
        raise AssertionError("应拒绝空 Key")
    except Exception as e:
        assert getattr(e, "status_code", None) == 401 or "密钥" in str(e)


def test_chat_accepts_correct_service_key():
    """带正确 X-Internal-Key 通过鉴权（不实际调用 LLM，仅验证校验逻辑）"""
    from app.config import settings
    import app.main as main_mod

    # 用配置的 key 校验应通过
    try:
        main_mod._check_service_key(settings.JAVA_INTERNAL_KEY)
    except Exception as e:  # 无 Key 异常才失败
        raise AssertionError(f"正确 Key 不应被拒绝: {e}")
