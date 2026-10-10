"""评价智能回复生成器与接口测试（LLM 用 stub，不依赖网络）"""
import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.review_reply.generator import build_reply_prompt, generate_reply, infer_label


class StubLLM:
    def __init__(self, reply: str = "亲，感谢您的支持！"):
        self.reply = reply
        self.calls = []

    def invoke(self, messages):
        self.calls.append(messages)
        if isinstance(self.reply, Exception):
            raise self.reply
        return AIMessage(content=self.reply)


PLATFORM = "测试平台"
PHONE = "400-000-0000"


def _gen(llm, label, rating=4, content="果子很新鲜", product="赣南脐橙"):
    return generate_reply(
        content=content, rating=rating, sentiment_label=label,
        product_name=product, llm=llm, platform=PLATFORM, service_phone=PHONE,
    )


class TestGenerator:
    def test_llm_path(self):
        r = _gen(StubLLM("亲，感谢您的认可，欢迎回购！"), label=1)
        assert r["source"] == "llm"
        assert r["reply"] == "亲，感谢您的认可，欢迎回购！"

    def test_llm_failure_falls_back(self):
        r = _gen(StubLLM(RuntimeError("429")), label=1)
        assert r["source"] == "fallback"
        assert "赣南脐橙" in r["reply"]  # 好评模板带上商品名

    def test_llm_empty_output_falls_back(self):
        r = _gen(StubLLM(""), label=1)
        assert r["source"] == "fallback"

    def test_llm_oversized_output_falls_back(self):
        r = _gen(StubLLM("亲，" + "长" * 400), label=1)
        assert r["source"] == "fallback"

    def test_negative_fallback_apologizes_and_compensates(self):
        r = _gen(StubLLM(RuntimeError("timeout")), label=-1, rating=1, content="坏果太多")
        reply = r["reply"]
        assert "抱歉" in reply
        assert "坏果包赔" in reply
        assert PHONE in reply  # 引导联系客服

    def test_neutral_fallback_mentions_improvement(self):
        r = _gen(StubLLM(RuntimeError("timeout")), label=0)
        assert "改进" in r["reply"]

    def test_positive_fallback_without_product(self):
        r = generate_reply(
            content="不错", rating=5, sentiment_label=1, product_name="",
            llm=StubLLM(RuntimeError("x")), platform=PLATFORM, service_phone=PHONE,
        )
        assert r["source"] == "fallback"
        assert "好物" in r["reply"]  # 无商品名退化为通用话术，不带空占位

    def test_prompt_carries_review_info_and_tone(self):
        prompt = build_reply_prompt(
            content="坏果多，很失望", rating=1, sentiment_label=-1,
            product_name="赣南脐橙", platform=PLATFORM, service_phone=PHONE,
        )
        assert "坏果多" in prompt
        assert "赣南脐橙" in prompt
        assert "差评" in prompt
        assert "道歉" in prompt  # 差评语气指令
        assert PHONE in prompt

        pos = build_reply_prompt("好吃", 5, 1, "五常大米", PLATFORM, PHONE)
        assert "感谢" in pos and "回购" in pos


class TestInferLabel:
    def test_sentiment_label_takes_priority(self):
        assert infer_label(-1, 5) == -1  # 情感结果优先于评分先验
        assert infer_label(1, 1) == 1

    def test_rating_prior_when_label_missing(self):
        assert infer_label(None, 5) == 1
        assert infer_label(None, 4) == 1
        assert infer_label(None, 2) == -1
        assert infer_label(None, 1) == -1

    def test_defaults_to_neutral(self):
        assert infer_label(None, 3) == 0
        assert infer_label(None, None) == 0


class TestReviewReplyEndpoint:
    @pytest.fixture()
    def client(self):
        from app.main import app

        return TestClient(app)

    def _headers(self):
        from app.config import settings

        return {"X-Internal-Key": settings.JAVA_INTERNAL_KEY}

    def _patch_llm(self, monkeypatch, llm):
        monkeypatch.setattr("app.main.get_llm", lambda: llm)

    def test_endpoint_returns_llm_draft(self, client, monkeypatch):
        self._patch_llm(monkeypatch, StubLLM("亲，感谢您的支持！"))
        resp = client.post(
            "/v1/review/reply",
            json={"reviewId": 123, "content": "很新鲜", "rating": 5,
                  "sentimentLabel": 1, "productName": "赣南脐橙"},
            headers=self._headers(),
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["reviewId"] == 123
        assert data["reply"] == "亲，感谢您的支持！"
        assert data["source"] == "llm"

    def test_endpoint_falls_back_when_llm_fails(self, client, monkeypatch):
        self._patch_llm(monkeypatch, StubLLM(RuntimeError("429")))
        resp = client.post(
            "/v1/review/reply",
            json={"reviewId": 456, "content": "坏果", "rating": 1,
                  "sentimentLabel": -1, "productName": "赣南脐橙"},
            headers=self._headers(),
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["source"] == "fallback"
        assert "坏果包赔" in data["reply"]

    def test_endpoint_rejects_missing_key(self, client):
        resp = client.post("/v1/review/reply", json={"reviewId": 1, "content": "x"})
        assert resp.status_code == 401

    def test_endpoint_validates_params(self, client):
        resp = client.post(
            "/v1/review/reply",
            json={"reviewId": 1, "rating": 9},  # rating 超出 1~5
            headers=self._headers(),
        )
        assert resp.status_code == 422
