"""长文档切块：溯源数据（生产记录 / 溯源档案）→ ai_faq 的 trace_knowledge 条目

需求文档 §6.2.2 把"长文档切分入库"列为 RAG 深度的最后一块。切入数据选了**真溯源事实**：
- production_record：播种/施肥/浇水/采收的生产过程记录（每条一个自然 chunk）；
- traceability_info：批次/种植户/质检/认证的溯源档案（多字段拼接后切块）。
商品详情 description 是模板文案（储存/保质期/产地已被 product_knowledge 覆盖），刻意不入。

切块进 ai_faq（category=trace_knowledge）而不是独立向量库：检索/确认带/引用日志/管理页
筛选/引用统计全部管线自动兼容，id 是自增 int 与现有 id 空间无冲突。
"""
import re

from sqlalchemy import text
from sqlalchemy.orm import Session

# 记录类型 → 中文标签（与 Java 侧 TraceabilityController 的口径一致）
_RECORD_TYPE_TEXT = {
    "SEEDING": "播种",
    "FERTILIZING": "施肥",
    "WATERING": "浇水",
    "PEST_CONTROL": "病虫害防治",
    "HARVEST": "采收",
}

_CHUNK_SIZE = 180
_CHUNK_OVERLAP = 30


def strip_html(html: str | None) -> str:
    """剥 HTML 标签与多余空白（商品详情/富文本通用）"""
    if not html:
        return ""
    text = re.sub(r"<[^>]+>", " ", html)
    text = re.sub(r"&[a-z]+;", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def split_chunks(text: str, size: int = _CHUNK_SIZE, overlap: int = _CHUNK_OVERLAP) -> list[str]:
    """按句子边界切块：优先在 。！？；\n 处断开，块间带 overlap 保证上下文连续。

    短文本（≤ size）原样返回单块；切分后过滤空白块。
    """
    text = (text or "").strip()
    if len(text) <= size:
        return [text] if text else []
    # 先按句子边界粗切
    sentences: list[str] = []
    for seg in re.split(r"(?<=[。！？；\n])", text):
        seg = seg.strip()
        if seg:
            sentences.append(seg)
    chunks: list[str] = []
    buf = ""
    for sent in sentences:
        if len(buf) + len(sent) <= size:
            buf += sent
            continue
        if buf:
            chunks.append(buf)
        # 单句超长：硬切并带 overlap 回退
        while len(sent) > size:
            chunks.append(sent[:size])
            sent = sent[size - overlap:]
        buf = sent
    if buf:
        chunks.append(buf)
    return [c for c in chunks if c.strip()]


def build_trace_entries(session: Session) -> list[dict]:
    """生产记录 + 溯源档案 → trace_knowledge 条目列表（{id 占位 0, question, answer, category, keywords}）"""
    entries: list[dict] = []

    # 1. 生产记录：每条记录一个自然 chunk（类型 + 日期 + 内容 + 商品锚点）
    rows = session.execute(
        text(
            """
            select pr.id, pr.record_type, pr.record_date, pr.content, pr.batch_no,
                   p.name as product_name, p.origin
            from production_record pr
            join product p on p.id = pr.product_id
            where p.deleted = 0
            order by pr.product_id, pr.record_date
            """
        )
    ).all()
    for r in rows:
        rec_id, rec_type, rec_date, content, batch_no, product_name, origin = r
        type_text = _RECORD_TYPE_TEXT.get(str(rec_type).upper(), str(rec_type))
        entries.append({
            "id": 0,  # 自增入库，占位
            "question": f"{product_name}的{type_text}记录",
            "answer": f"{product_name}（批次 {batch_no}）{type_text}记录：{rec_date}，{strip_html(content)}",
            "category": "trace_knowledge",
            "keywords": ",".join(x for x in (product_name, type_text, str(origin or "")) if x),
        })

    # 2. 溯源档案：多字段拼一段，超长时切块
    rows = session.execute(
        text(
            """
            select t.id, t.batch_no, t.farmer_name, t.origin_province, t.origin_city,
                   t.harvest_date, t.quality_check_result, t.certification_type,
                   p.name as product_name
            from traceability_info t
            join product p on p.id = t.product_id
            where p.deleted = 0
            """
        )
    ).all()
    for r in rows:
        (t_id, batch_no, farmer, prov, city, harvest, quality, cert, product_name) = r
        parts = [f"批次 {batch_no}"]
        if farmer:
            parts.append(f"种植户 {farmer}")
        if prov or city:
            parts.append(f"产地 {prov or ''}{city or ''}")
        if harvest:
            parts.append(f"采摘日期 {harvest}")
        if quality:
            parts.append(f"质检 {quality}")
        if cert:
            parts.append(f"认证 {cert}")
        body = "｜".join(str(x) for x in parts if x)
        for i, chunk in enumerate(split_chunks(body)):
            suffix = f"（第{i + 1}段）" if i else ""
            entries.append({
                "id": 0,
                "question": f"{product_name}的溯源档案{suffix}",
                "answer": f"{product_name}溯源档案：{chunk}",
                "category": "trace_knowledge",
                "keywords": ",".join(x for x in (product_name, "溯源", str(batch_no or "")) if x),
            })
    return entries
