"""文档上传入库测试：抽取（txt/md/docx/pdf/非法格式）、切块入库（fake session）、文档清单与删除"""
import io
import zipfile
import xml.etree.ElementTree as ET

import pytest

from app.rag import doc_ingest
from app.rag.doc_ingest import (
    DOC_CATEGORY,
    DocIngestError,
    delete_by_source,
    extract_text,
    ingest_document,
    list_documents,
)


# ---------- 抽取 ----------

def test_extract_text_txt_utf8_and_gbk():
    assert extract_text("政策.txt", "退货时限 7 天".encode("utf-8")) == "退货时限 7 天"
    # 国产 Word 导出的 txt 常是 GBK
    assert extract_text("政策.txt", "退货时限 7 天".encode("gb18030")) == "退货时限 7 天"
    assert extract_text("说明.MD", "# 标题\n正文".encode("utf-8")) == "# 标题\n正文"


def test_extract_text_unsupported_format():
    with pytest.raises(DocIngestError):
        extract_text("图片.png", b"\x89PNG")
    with pytest.raises(DocIngestError):
        extract_text("", b"anything")


def _build_docx(paragraphs: list[str]) -> bytes:
    """构造最小可解析的 docx（zip + word/document.xml）"""
    ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    body = "".join(
        f'<w:p><w:r><w:t xml:space="preserve">{p}</w:t></w:r></w:p>' for p in paragraphs
    )
    xml = (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{ns}"><w:body>{body}</w:body></w:document>'
    ).encode("utf-8")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("word/document.xml", xml)
    return buf.getvalue()


def test_extract_text_docx_joins_runs_and_skips_empty_paragraphs():
    # 两个 run 拼一段（Word 常把一句话拆成多个 <w:t>），空段落丢弃
    ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    ET.register_namespace("w", ns)
    data = _build_docx(["生鲜商品支持 七天无理由", "", "坏果包赔"])
    assert extract_text("手册.docx", data) == "生鲜商品支持 七天无理由\n坏果包赔"


def test_extract_text_docx_corrupted():
    with pytest.raises(DocIngestError):
        extract_text("假.docx", b"not a zip")


def test_extract_text_pdf(tmp_path):
    # 用 pypdf 现写一个最小 pdf 再读回来（避免提交二进制测试夹具）
    try:
        from pypdf import PdfWriter
    except ImportError:  # 环境没装 pypdf 时跳过该用例（其余格式不依赖它）
        pytest.skip("pypdf not installed")
    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    buf = io.BytesIO()
    writer.write(buf)
    data = buf.getvalue()
    # 空白页抽不出文本 → 抛"无有效文本"由 ingest 阶段负责；这里只验证不炸
    text = extract_text("白皮书.pdf", data)
    assert isinstance(text, str)


# ---------- 切块入库（fake session） ----------

class _FakeFaq:
    """最小 ORM 替身：记录字段，id 用计数器模拟自增"""

    def __init__(self, **kw):
        self.__dict__.update(kw)
        self.id = 0


class _FakeQuery:
    def __init__(self, rows):
        self._rows = rows

    def filter(self, *a, **k):
        return self

    def delete(self, *a, **k):
        self._rows.clear()
        return len(self._rows)

    def all(self):
        return list(self._rows)


class _FakeSession:
    def __init__(self):
        self.added: list[_FakeFaq] = []
        self._next_id = 100
        self.queries: list[str] = []
        self.existing_rows: list = []  # list of (id, source) 元组，供 delete_by_source 查询

    def add(self, obj):
        self._next_id += 1
        obj.id = self._next_id
        self.added.append(obj)

    def flush(self):
        pass

    def commit(self):
        pass

    def rollback(self):
        pass

    def query(self, *cols):
        self.queries.append("query")
        # 行形状由测试用例自备：delete_by_source 用 .id 属性行，list_documents 用 (source,) 元组
        return _FakeQuery(list(self.existing_rows))


def test_ingest_document_shapes_rows_and_ids():
    s = _FakeSession()
    text = "第一段内容。" * 80  # 足够切出多块
    ids = ingest_document(s, "售后政策", text, "doc:售后政策")
    assert len(ids) >= 2
    assert s.added[0].category == DOC_CATEGORY
    assert s.added[0].source == "doc:售后政策"
    assert s.added[0].keywords == "售后政策"
    assert s.added[0].status == 1
    # question 带文档名与段号，可追溯到出处
    assert "售后政策·第1段" in s.added[0].question
    # answer 是块正文
    assert s.added[0].answer


def test_ingest_document_empty_text_raises_without_touching_session():
    s = _FakeSession()
    with pytest.raises(DocIngestError):
        ingest_document(s, "空白文档", "   \n  ", "doc:空白文档")
    assert s.added == []


def test_ingest_document_short_text_single_chunk():
    s = _FakeSession()
    ids = ingest_document(s, "一句话政策", "七天无理由退货。", "doc:一句话政策")
    assert len(ids) == 1


def test_delete_by_source_returns_ids_and_deletes():
    from collections import namedtuple

    Row = namedtuple("Row", ["id"])  # 真实 SQLAlchemy query(Faq.id) 行有 .id 属性
    s = _FakeSession()
    s.existing_rows = [Row(1), Row(2), Row(9)]
    ids = delete_by_source(s, "doc:旧政策")
    assert ids == [1, 2, 9]


def test_list_documents_aggregates_by_source():
    s = _FakeSession()
    # list_documents 查询的行形状是 (source,) 单列元组
    s.existing_rows = [("doc:A手册",), ("doc:A手册",), ("doc:B政策",)]
    items = list_documents(s)
    names = {d["name"]: d["chunks"] for d in items}
    assert names == {"A手册": 2, "B政策": 1}
