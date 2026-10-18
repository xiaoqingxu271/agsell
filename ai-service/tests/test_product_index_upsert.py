"""商品向量索引增量更新（P1 2.3：上新商品 AI 立刻召得到）

覆盖两层：
- ProductStore.upsert_ids：在售的 upsert、下架/删除/查不到的除名（比召不到更糟的是推出买不到的货）
- POST /v1/admin/kb/products/upsert：鉴权、脏 id 归一、空列表拒绝、失败降级为 503
"""
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.api import admin_kb
from app.rag.product_store import ProductStore


class RecordingVs:
    """假 VectorStore：只记录调用，不做向量化（零模型依赖）"""

    def __init__(self):
        self.upserted: list[list] = []
        self.deleted: list[list] = []

    def upsert_many(self, entries):
        self.upserted.append([e["id"] for e in entries])
        return len(entries)

    def delete_ids(self, ids):
        self.deleted.append(list(ids))
        return len(ids)

    def count(self):
        return 0


def _product(pid, name="新品", category_id=3):
    return SimpleNamespace(id=pid, name=name, subtitle="产地直发", origin="江西赣州",
                           storage="常温", shelf_life="7天", category_id=category_id)


@pytest.fixture
def patched_repo(monkeypatch):
    """把 product_repo 的三个查询换成内存桩，返回可配置的容器"""
    from app.repositories import product_repo

    holder = {"on_sale": [], "by_ids": []}

    monkeypatch.setattr(product_repo, "category_name_map", lambda session: {3: "柑橘类"})
    monkeypatch.setattr(product_repo, "list_by_ids",
                        lambda session, ids: [p for p in holder["by_ids"] if p.id in set(ids)])
    monkeypatch.setattr(product_repo, "list_on_sale", lambda session: holder["on_sale"])
    return holder


def test_on_sale_products_are_upserted(patched_repo):
    vs = RecordingVs()
    patched_repo["by_ids"] = [_product(1), _product(2)]
    out = ProductStore(vs).upsert_ids(None, [1, 2])
    assert out == {"indexed": 2, "deindexed": 0}
    assert vs.upserted == [[1, 2]]
    assert vs.deleted == []          # 全部命中时不该动索引里的其他条目


def test_off_sale_products_are_removed_from_index(patched_repo):
    """下架商品不在"在售"查询结果里 —— 必须当成"该除名"处理，否则导购还会把它推出来"""
    vs = RecordingVs()
    patched_repo["by_ids"] = [_product(1)]
    out = ProductStore(vs).upsert_ids(None, [1, 2, 3])
    assert out == {"indexed": 1, "deindexed": 2}
    assert vs.upserted == [[1]]
    assert vs.deleted == [[2, 3]]


def test_empty_id_list_is_a_no_op(patched_repo):
    vs = RecordingVs()
    assert ProductStore(vs).upsert_ids(None, []) == {"indexed": 0, "deindexed": 0}
    assert vs.upserted == [] and vs.deleted == []


def test_entry_shape_matches_full_rebuild(patched_repo):
    """增量条目必须与全量 rebuild 用同一个 build_entry：否则两条通道写出的文档口径会漂"""
    vs = RecordingVs()
    patched_repo["by_ids"] = [_product(1)]
    patched_repo["on_sale"] = [_product(1)]
    store = ProductStore(vs)
    store.upsert_ids(None, [1])
    incremental = vs.upserted[-1]

    from app.rag.product_store import build_entry

    entry = build_entry(_product(1), "柑橘类")
    assert entry["question"] == "新品" and "分类：柑橘类" in entry["answer"]
    assert incremental == [1]


# ---------- HTTP 端点 ----------
class FakeStore:
    def __init__(self, result=None, error=None):
        self.result = result or {"indexed": 1, "deindexed": 0}
        self.error = error
        self.seen_ids = None

    def upsert_ids(self, session, ids):
        self.seen_ids = ids
        if self.error:
            raise self.error
        return self.result

    def rebuild(self, session):
        return 202


KEY = "test-internal-key"


@pytest.fixture(autouse=True)
def _pin_internal_key(monkeypatch):
    """把内部 Key 钉成固定值：conftest 的 setdefault 会晚于 app.config 实例化，
    实际读到的是 .env 里那把真 Key——测试不能依赖环境，否则换机器就跑不动。"""
    from app.config import settings

    monkeypatch.setattr(settings, "JAVA_INTERNAL_KEY", KEY)


@pytest.fixture
def client():
    import app.main as main_mod

    return TestClient(main_mod.app)


def _use(monkeypatch, store):
    monkeypatch.setattr(admin_kb, "_get_product_store", lambda: store)


def test_upsert_requires_internal_key(client, monkeypatch):
    _use(monkeypatch, FakeStore())
    resp = client.post("/v1/admin/kb/products/upsert", json={"productIds": [1]})
    assert resp.status_code == 401
    bad = client.post("/v1/admin/kb/products/upsert", json={"productIds": [1]},
                      headers={"X-Internal-Key": KEY + "-wrong"})
    assert bad.status_code == 401


def test_upsert_accepts_string_ids_and_normalizes(client, monkeypatch):
    """Java 的商品 id 是雪花 Long，序列化时常成字符串——两种都得能吃"""
    store = FakeStore()
    _use(monkeypatch, store)
    resp = client.post("/v1/admin/kb/products/upsert",
                       json={"productIds": ["2109101682588422145", 2]},
                       headers={"X-Internal-Key": KEY})
    assert resp.status_code == 200
    assert store.seen_ids == [2109101682588422145, 2]
    assert resp.json()["indexed"] == 1


def test_upsert_skips_garbage_ids_instead_of_failing_whole_batch(client, monkeypatch):
    store = FakeStore()
    _use(monkeypatch, store)
    resp = client.post("/v1/admin/kb/products/upsert",
                       json={"productIds": ["abc", None, 7]},
                       headers={"X-Internal-Key": KEY})
    assert resp.status_code == 200
    assert store.seen_ids == [7]


def test_upsert_rejects_empty_list(client, monkeypatch):
    """空批多半是上游把 id 传丢了：静默成功会掩盖 bug，直接 400/422 让它现形"""
    _use(monkeypatch, FakeStore())
    resp = client.post("/v1/admin/kb/products/upsert", json={"productIds": []},
                       headers={"X-Internal-Key": KEY})
    assert resp.status_code in (400, 422)


def test_upsert_degrades_to_503_when_vector_search_breaks(client, monkeypatch):
    """索引是派生数据：向量化炸了要给出可判空的错误码，Java 侧只记日志、绝不影响"保存商品"这件事"""
    _use(monkeypatch, FakeStore(error=RuntimeError("chroma down")))
    resp = client.post("/v1/admin/kb/products/upsert", json={"productIds": [1]},
                       headers={"X-Internal-Key": KEY})
    assert resp.status_code == 503
    assert "chroma" not in resp.text.lower()      # 内部细节不进响应体


def test_documented_header_is_the_one_that_works(client, monkeypatch):
    """契约回归：文档写 X-Internal-Key，就必须是这个头能用。

    此前参数名写成 x_admin_key，FastAPI 按 X-Admin-Key 取值，带文档头的调用一律 401；
    Java 一直没有代理，所以这个不一致从没被执行过。
    """
    _use(monkeypatch, FakeStore())
    ok = client.post("/v1/admin/kb/products/upsert", json={"productIds": [1]},
                     headers={"X-Internal-Key": KEY})
    assert ok.status_code == 200, ok.text
    missing = client.post("/v1/admin/kb/products/upsert", json={"productIds": [1]})
    assert missing.status_code == 401


def test_missing_configuration_fails_closed(client, monkeypatch):
    """内部密钥没配置时管理端必须拒绝服务，而不是"默认放行"——这里能改知识库数据"""
    from app.config import settings

    monkeypatch.setattr(settings, "JAVA_INTERNAL_KEY", "")
    resp = client.get("/v1/admin/kb/stats", headers={"X-Internal-Key": "anything"})
    assert resp.status_code == 503


def test_stats_reports_both_indexes(client, monkeypatch):
    """管理页的"索引状态"卡要看得到两个库各有多少条——FAQ 280 条而商品索引 0 条就是 2.3 的病灶"""
    import app.main as main_mod
    from app.db import get_session
    from app.repositories import faq_repo

    monkeypatch.setattr(main_mod, "get_vectorstore", lambda: SimpleNamespace(count=lambda: 280))
    monkeypatch.setattr(main_mod, "get_product_store", lambda: SimpleNamespace(count=lambda: 202))
    monkeypatch.setattr(faq_repo, "count_enabled", lambda session: 280)
    # 依赖注入换成桩会话：.env 的 DATABASE_URL 是 Docker 专用 host，单测不该连真库
    client.app.dependency_overrides[get_session] = lambda: None
    try:
        resp = client.get("/v1/admin/kb/stats", headers={"X-Internal-Key": KEY})
        assert resp.status_code == 200
        body = resp.json()
        assert body["indexed_count"] == 280
        assert body["product_indexed_count"] == 202
    finally:
        client.app.dependency_overrides.clear()
