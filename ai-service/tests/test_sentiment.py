"""情感分析器与批量接口测试"""
import pytest
from fastapi.testclient import TestClient

from app.sentiment.analyzer import analyze_sentiment


class TestAnalyzer:
    def test_positive_review(self):
        r = analyze_sentiment("非常好吃的脐橙，新鲜多汁！", 5)
        assert r["label"] == 1
        assert r["score"] >= 0.7
        assert "新鲜" in r["keywords"]

    def test_negative_review(self):
        r = analyze_sentiment("坏果太多，已经申请退款", 1)
        assert r["label"] == -1
        assert "坏果" in r["keywords"]

    def test_negation_flips_polarity(self):
        r = analyze_sentiment("不好吃，有点烂了", 1)
        assert r["label"] == -1
        # 否定翻转的词以「不X」进关键词，避免差评口碑出现正面词
        assert "不好吃" in r["keywords"]
        assert "好吃" not in r["keywords"]

    def test_negation_window(self):
        assert analyze_sentiment("不太新鲜", 2)["label"] == -1

    def test_neutral_no_hits(self):
        r = analyze_sentiment("一般般吧", 3)
        assert r["label"] == 0
        assert r["score"] == 0.5
        assert r["keywords"] == []

    def test_rating_prior_short_text(self):
        """无情感词的短文本：评分先验决定倾向"""
        assert analyze_sentiment("嗯", 5)["score"] > 0.5
        assert analyze_sentiment("嗯", 1)["score"] < 0.5

    def test_mild_complaint_not_negative(self):
        """4 星 + 轻微抱怨 → 混合评价判中评及以上，不因小抱怨一票否决为差评"""
        r = analyze_sentiment("发货有点慢，其他还行", 4)
        assert r["label"] in (0, 1)
        assert r["score"] >= 0.5

    def test_empty_content(self):
        r = analyze_sentiment("", 5)
        assert r["label"] == 0
        assert r["keywords"] == []

    def test_score_in_range(self):
        for text in ("好吃", "烂了", "一般", "abcdefgh", "好吃！但是有点慢"):
            assert 0.0 <= analyze_sentiment(text)["score"] <= 1.0


class TestBatchEndpoint:
    @pytest.fixture()
    def client(self):
        from app.main import app

        return TestClient(app)

    def _headers(self):
        from app.config import settings

        return {"X-Internal-Key": settings.JAVA_INTERNAL_KEY}

    def test_batch_analysis(self, client):
        resp = client.post(
            "/v1/sentiment/batch",
            json={"items": [
                {"reviewId": 1, "content": "新鲜好吃，会回购", "rating": 5},
                {"reviewId": 2, "content": "坏果，失望", "rating": 1},
            ]},
            headers=self._headers(),
        )
        assert resp.status_code == 200
        results = resp.json()["results"]
        assert results[0]["label"] == 1
        assert results[1]["label"] == -1
        assert results[0]["reviewId"] == 1

    def test_auth_required(self, client):
        resp = client.post("/v1/sentiment/batch", json={"items": [{"reviewId": 1}]})
        assert resp.status_code == 401
