"""文档上传入库：管理端上传 txt/md/docx/pdf → 抽文本 → 切块 → ai_faq(doc_knowledge) → 向量索引

知识库此前的内容生产只有两条路：手写 FAQ 与商品/溯源数据派生条目，运营手里的
产品手册、售后政策全文没有入口。本模块补上"上传即入库"链路：

- 抽取：txt/md 直接解码（utf-8 失败退 gb18030，国产 Word 导出的 txt 常见）；
  docx 用 stdlib zipfile+XML 抽 <w:t> 段落（docx 本质是 zip，不引重依赖）；
  pdf 用 pypdf 逐页抽（纯 Python 实现，规避 PyMuPDF 的二进制轮子）。
- 切块：复用 chunker.split_chunks，文档用更大的块（320/60）——政策条文一句话
  常超 100 字，沿用溯源数据的 180/30 会把一条条款拦腰截断。
- 落库：每块一条 ai_faq（category=doc_knowledge），source 列记 doc:{文档名}，
  删除文档按 source 整批下架；同名文档重复上传 = 先删旧块再入新块（幂等替换）。
  question 带文档名与段号，命中后的"依据"标签能追溯到出处文档。
"""
import io
import zipfile
import xml.etree.ElementTree as ET

from sqlalchemy.orm import Session

from app.models.faq import Faq
from app.rag.chunker import split_chunks

DOC_CATEGORY = "doc_knowledge"

# 文档块比溯源块大：政策/手册以条款为单位，切太碎会把"退款时限"这类关键句拆散
_DOC_CHUNK_SIZE = 320
_DOC_CHUNK_OVERLAP = 60

# question 里的块首锚点长度：太短检索区分度不够，太长挤占 255 列
_QUESTION_HEAD = 40

_MAX_TITLE_LEN = 60


class DocIngestError(ValueError):
    """文档解析/入库失败（不支持的格式、文件损坏、无有效文本）→ 管理端 400"""


def extract_text(filename: str, data: bytes) -> str:
    """按扩展名分派抽取。统一返回纯文本（段落以 \n 分隔），失败抛 DocIngestError。"""
    name = (filename or "").lower()
    if name.endswith((".txt", ".md")):
        return _decode(data)
    if name.endswith(".docx"):
        return _extract_docx(data)
    if name.endswith(".pdf"):
        return _extract_pdf(data)
    raise DocIngestError("仅支持 txt / md / docx / pdf 格式")


def ingest_document(session: Session, title: str, text: str, source: str) -> list[int]:
    """切块并写入 ai_faq（不提交、不同步索引——事务与向量同步由调用方编排）。

    调用方若要实现"同名文档替换"，先自行 delete_by_source 再调本函数；
    本函数只负责把切块全部写进来并返回 id（flush 取自增 id，供索引 upsert）。
    """
    title = _clean_title(title)
    chunks = split_chunks(text, size=_DOC_CHUNK_SIZE, overlap=_DOC_CHUNK_OVERLAP)
    if not chunks:
        raise DocIngestError("文档没有可用的文本内容")
    ids: list[int] = []
    for i, chunk in enumerate(chunks, 1):
        head = chunk[:_QUESTION_HEAD]
        suffix = f"：{head}" if head else ""
        faq = Faq(
            question=f"{title}·第{i}段{suffix}"[:255],
            answer=chunk,
            category=DOC_CATEGORY,
            keywords=title,
            status=1,
            source=source,
        )
        session.add(faq)
        session.flush()  # 拿自增 id，供调用方 upsert 索引
        ids.append(faq.id)
    return ids


def delete_by_source(session: Session, source: str) -> list[int]:
    """按来源整批删除（返回被删 id，调用方负责从向量索引除名）"""
    rows = list(session.query(Faq.id).filter(Faq.source == source).all())
    ids = [r.id for r in rows]
    if ids:
        session.query(Faq).filter(Faq.source == source).delete(synchronize_session=False)
        session.flush()
    return ids


def list_documents(session: Session) -> list[dict]:
    """已入库文档清单：按 source 聚合块数（仅 doc: 前缀，不含手写/脚本条目）"""
    rows = session.query(Faq.source).filter(Faq.source.like("doc:%")).all()
    docs: dict[str, dict] = {}
    for (source,) in rows:
        name = source[len("doc:"):] or source
        doc = docs.setdefault(source, {"name": name, "chunks": 0})
        doc["chunks"] += 1
    return sorted(docs.values(), key=lambda d: d["name"])


def _clean_title(title: str | None, fallback: str = "未命名文档") -> str:
    title = (title or "").strip() or fallback
    return title[:_MAX_TITLE_LEN]


def _decode(data: bytes) -> str:
    for enc in ("utf-8", "gb18030"):
        try:
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="ignore")


def _extract_docx(data: bytes) -> str:
    """docx = zip 包着 word/document.xml，段落文本在 <w:t> 节点里，一段一个 <w:p>"""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            xml_bytes = zf.read("word/document.xml")
        root = ET.fromstring(xml_bytes)
    except (zipfile.BadZipFile, KeyError, ET.ParseError) as e:
        raise DocIngestError("docx 解析失败：文件可能已损坏") from e
    ns = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
    lines = []
    for para in root.iter(f"{ns}p"):
        line = "".join(t.text or "" for t in para.iter(f"{ns}t")).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def _extract_pdf(data: bytes) -> str:
    from pypdf import PdfReader  # 延迟导入：只在真收到 pdf 时才加载

    try:
        reader = PdfReader(io.BytesIO(data))
        pages = []
        for page in reader.pages:
            text = (page.extract_text() or "").strip()
            if text:
                pages.append(text)
    except Exception as e:  # pypdf 对损坏/加密文件抛的是各种异常，统一归一
        raise DocIngestError("pdf 解析失败：文件可能已损坏或已加密") from e
    return "\n".join(pages)
