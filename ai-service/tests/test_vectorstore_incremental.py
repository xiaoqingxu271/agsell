"""阶段七：FAQ 向量库增量索引（upsert 幂等 / delete 忽略不存在 / rebuild 全量替换）

用临时目录 + FakeEmbedder（确定性向量、零模型下载、零网络），仅验证索引增删改行为。
"""
import numpy as np
import pytest

from app.rag.vectorstore import VectorStore


class FakeEmbedder:
    """固定 8 维确定性向量：同一文本在单次进程内向量一致，维度统一即可写入 Chroma。"""

    dim = 8

    def _vec(self, text: str) -> np.ndarray:
        seed = abs(hash(text)) % (2**32)
        return np.random.RandomState(seed).rand(self.dim) + 0.1  # +0.1 避免零向量

    def embed(self, docs):
        return [self._vec(d) for d in docs]

    def query_embed(self, q):
        return [self._vec(q)]


def _entry(id, q="问题", a="答案", category="c", keywords="k"):
    return {"id": id, "question": q, "answer": a, "category": category, "keywords": keywords}


@pytest.fixture
def vs(tmp_path):
    return VectorStore(path=str(tmp_path), embedder=FakeEmbedder())


def test_upsert_is_idempotent(vs):
    assert vs.count() == 0
    assert vs.upsert(_entry(1, "运费多少", "运费规则")) == 1
    assert vs.count() == 1

    # 同 id 再次写入 = 更新而非新增
    assert vs.upsert(_entry(1, "运费多少（改）", "新答案")) == 1
    assert vs.count() == 1
    got = vs._collection.get(ids=["1"])
    assert "新答案" in got["documents"][0]


def test_upsert_many_and_delete(vs):
    assert vs.upsert_many([_entry(1), _entry(2), _entry(3)]) == 3
    assert vs.count() == 3

    assert vs.delete_ids([2]) == 1
    assert vs.count() == 2
    assert vs._collection.get(ids=["2"])["ids"] == []

    # 删除不存在的 id 不报错、返回 0；空批量 upsert 为 0
    assert vs.delete_ids([999]) == 0
    assert vs.delete_ids([]) == 0
    assert vs.upsert_many([]) == 0


def test_rebuild_replaces_all(vs):
    vs.upsert_many([_entry(1), _entry(2)])
    assert vs.count() == 2
    n = vs.rebuild([_entry(3)])
    assert n == 1
    assert vs.count() == 1
    assert vs._collection.get(ids=["1"])["ids"] == []
    assert vs._collection.get(ids=["3"])["ids"] == ["3"]
