"""知识库管理接口（阶段二；本轮补商品索引增量通道与文档上传入库）

- GET  /v1/admin/kb/stats   知识库状态（FAQ 条数 + FAQ/商品两个索引条数 + 模型与阈值）
- POST /v1/admin/kb/index   触发 FAQ 全量重建（读 MySQL → bge 向量化 → 写 Chroma）
- POST /v1/admin/kb/products/index  触发商品召回索引全量重建（管理页兜底按钮）
- POST /v1/admin/kb/products/upsert 按商品 id 增量更新（Java 商品变更后异步调用）
- GET  /v1/admin/kb/faqs    FAQ 列表（分页/分类）
- POST /v1/admin/kb/faqs    新增 FAQ
- PUT  /v1/admin/kb/faqs/{id} 更新 FAQ
- DELETE /v1/admin/kb/faqs/{id} 删除 FAQ
- POST /v1/admin/kb/documents       上传文档（txt/md/docx/pdf → 抽文本 → 切块 → 入库+索引）
- GET  /v1/admin/kb/documents       已入库文档清单（按来源聚合块数）
- DELETE /v1/admin/kb/documents?name=  删除文档（整批下架 + 从索引除名）

鉴权：全部端点要求 X-Internal-Key（= JAVA_INTERNAL_KEY），由 Java 管理端代理携带；
Java 侧再套自己的 JWT 与角色校验，浏览器永远拿不到这个 Key。

提示：新增/修改/删除 FAQ 后自动增量同步向量库；也可随时调用 POST /index 全量重建。
商品侧不再需要手动重建：Java 在商品新增/编辑/上下架/删除成功后异步 POST products/upsert，
在售的 upsert、下架与删除的除名；全量重建按钮保留作兜底（索引是派生数据，MySQL 才是源）。
"""
import logging

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, Query, UploadFile
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.config import settings
from app.db import SessionLocal, get_session
from app.rag import doc_ingest
from app.rag.vectorstore import VectorStore
from app.repositories import faq_repo

logger = logging.getLogger("agsell.ai.kb")

router = APIRouter(prefix="/admin/kb", tags=["知识库管理"])


class FaqCreate(BaseModel):
    question: str = Field(..., min_length=1, max_length=255)
    answer: str = Field(..., min_length=1)
    category: str = Field(default="general", max_length=50)
    keywords: str = Field(default="", max_length=255)
    status: int = Field(default=1, ge=0, le=1)


class FaqUpdate(BaseModel):
    question: str | None = Field(default=None, min_length=1, max_length=255)
    answer: str | None = Field(default=None, min_length=1)
    category: str | None = Field(default=None, max_length=50)
    keywords: str | None = Field(default=None, max_length=255)
    status: int | None = Field(default=None, ge=0, le=1)


class ProductIndexRequest(BaseModel):
    """商品变更通知：Java 在新增/编辑/上下架/删除成功后异步送来的 id 列表。

    元素允许是数字或字符串（Java 侧商品 id 是雪花 Long，序列化给前端时常转字符串），
    这里统一 int() 归一，脏值跳过而不是整批报错。
    """

    # 元素类型故意放宽：上游是 Java，可能送数字也可能送字符串，还可能有 null。
    # 这里在端点内逐个归一成 int 并跳过脏值，比在 schema 层直接 422 更贴近实际——
    # 一颗脏 id 不该让整批商品的索引都不更新。
    productIds: list = Field(default_factory=list, min_length=1, max_length=200)


class ConfigUpdate(BaseModel):
    """AI 运行参数批量更新：{key: 字符串值}。只处理白名单键，其余忽略。"""

    values: dict[str, str] = Field(default_factory=dict)


def _check_admin_key(x_internal_key: str | None = Header(default=None)) -> None:
    """管理端点鉴权：只认 X-Internal-Key（与其余服务间端点同名），且必须已配置（fail-closed）。

    两处坑都在这条上：
    ① 参数此前写作 x_admin_key，FastAPI 会按 X-Admin-Key 取值，与本文件头部声明的
       X-Internal-Key 对不上——带文档所说头的调用一律 401。Java 侧一直没有代理，
       所以这条从没被真实跑过，直到本轮接管理端才暴露。
    ② 旧写法 `if settings.JAVA_INTERNAL_KEY and ...` 在密钥未配置时是**放行**的；
       管理端能改知识库数据，缺配置必须拒绝，不能默认开放。
    ALLOW_ANON_CHAT 不参与这里的判断：那是本地调试聊天接口用的开关，
    用它绕过管理端写入等于给知识库留了个后门。
    """
    if not settings.JAVA_INTERNAL_KEY:
        raise HTTPException(status_code=503, detail="服务未配置内部密钥")
    if not x_internal_key or x_internal_key != settings.JAVA_INTERNAL_KEY:
        raise HTTPException(status_code=401, detail="无效的访问密钥")


@router.get("/stats")
def stats(session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    from app import kb_hit_log  # 延迟导入避免循环依赖

    return {
        "faq_count": faq_repo.count_enabled(session),
        "indexed_count": _get_vectorstore().count(),
        "product_indexed_count": _get_product_store().count(),
        "embedding_model": settings.EMBEDDING_MODEL,
        "vector_db_path": settings.VECTOR_DB_PATH,
        "top_k": settings.KB_TOP_K,
        "product_top_k": settings.PRODUCT_TOP_K,
        # 近 7 日被 AI 引用 Top（运营视图：哪条知识真的被用过）；日志库缺失/无数据返回空
        "recent_hit_top": kb_hit_log.recent_top(
            settings.KB_HIT_LOG_PATH,
            days=settings.KB_HIT_TOP_DAYS,
            limit=settings.KB_HIT_TOP_LIMIT,
        ),
    }


@router.post("/index")
def rebuild_index(session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    """全量重建索引：读启用 FAQ → 向量化 → 写 Chroma（几十条条目只需几秒）"""
    entries = [
        {
            "id": f.id,
            "question": f.question,
            "answer": f.answer,
            "category": f.category,
            "keywords": f.keywords,
        }
        for f in faq_repo.list_enabled(session)
    ]
    n = _get_vectorstore().rebuild(entries)
    return {"indexed": n, "model": settings.EMBEDDING_MODEL}


@router.post("/products/index")
def rebuild_product_index(_=Depends(_check_admin_key)):
    """全量重建商品召回索引：读在售商品（MySQL 为准）→ 向量化 → 写 Chroma(product)。

    知识库管理页的"全量重建"按钮走这里；增量通道（下面 products/upsert）失灵或
    历史脏数据积累时，这是唯一的兜底修复手段，202 件在售商品重建只需几秒。
    """
    session = SessionLocal()
    try:
        n = _get_product_store().rebuild(session)
    finally:
        session.close()
    return {"indexed": n, "model": settings.EMBEDDING_MODEL}


@router.post("/products/upsert")
def upsert_product_index(body: ProductIndexRequest, _=Depends(_check_admin_key)):
    """按商品 id 增量更新召回索引（Java 在商品新增/编辑/上下架成功后异步调用）。

    在售 → upsert；已下架/已删除/查不到 → 从索引除名。整条链路必须 fail-open：
    索引是派生数据，向量化再慢再挂也不能让管理员的"保存商品"变成失败。
    MySQL 才是数据源，实在不同步时页面上的全量重建按钮随时能修好。
    """
    ids = []
    for raw in body.productIds or []:
        try:
            ids.append(int(str(raw)))
        except (TypeError, ValueError):
            continue  # 脏 id 直接跳过，不因此中断整批
    if not ids:
        raise HTTPException(status_code=400, detail="productIds 不能为空")
    session = SessionLocal()
    try:
        return {**_get_product_store().upsert_ids(session, ids), "model": settings.EMBEDDING_MODEL}
    except Exception as e:
        logger.exception("商品索引增量更新失败：productIds=%s", ids)
        # 与 Java 侧 AiSearchClient 同口径：返回可判空的降级结果，不抛 500 让上游猜
        raise HTTPException(status_code=503, detail=f"商品索引更新失败：{type(e).__name__}") from e
    finally:
        session.close()


@router.get("/ops")
def ops(days: int = Query(default=7, ge=1, le=30), _=Depends(_check_admin_key)):
    """近 N 日 AI 运营汇总（对话量/拒答率/工具分布/按日趋势）。

    数据源是 chat_log（每轮一条，main.py 落库）；日志库缺失/无数据返回零值结构，
    前端据此显示空态而不是报错。
    """
    from app import kb_hit_log  # 延迟导入避免循环依赖

    return kb_hit_log.ops_summary(settings.KB_HIT_LOG_PATH, days=days)


@router.get("/config")
def get_ai_config(_=Depends(_check_admin_key)):
    """AI 运行参数快照（当前值/默认值/是否被覆盖/边界），供管理端「AI 运营配置」页展示"""
    from app import runtime_config

    return {"items": runtime_config.snapshot()}


@router.put("/config")
def put_ai_config(body: ConfigUpdate, _=Depends(_check_admin_key)):
    """更新 AI 运行参数（sys_config 的 ai_* 白名单键，PUT 后立即生效无需重启）。

    值非法（非数值/越界之外解析失败）整批 400；越界值会被夹取到边界并生效。
    """
    from app import runtime_config

    try:
        updated = runtime_config.set_values(body.values or {})
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return {"updated": updated, "items": runtime_config.snapshot()}


@router.get("/faqs")
def list_faqs(
    category: str | None = None,
    page: int = Query(default=1, ge=1),
    size: int = Query(default=20, ge=1, le=100),
    session: Session = Depends(get_session),
    _=Depends(_check_admin_key),
):
    items, total = faq_repo.list_all(session, category=category, page=page, size=size)
    return {
        "total": total,
        "items": [
            {
                "id": f.id,
                "question": f.question,
                "answer": f.answer,
                "category": f.category,
                "keywords": f.keywords,
                "status": f.status,
                "updated_at": str(f.updated_at) if f.updated_at else None,
            }
            for f in items
        ],
    }


@router.post("/faqs", status_code=201)
def create_faq(body: FaqCreate, session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    f = faq_repo.create(
        session, body.question, body.answer, body.category, body.keywords, body.status
    )
    return {"id": f.id, "indexed": _sync_upsert(f)}


@router.put("/faqs/{faq_id}")
def update_faq(
    faq_id: int,
    body: FaqUpdate,
    session: Session = Depends(get_session),
    _=Depends(_check_admin_key),
):
    f = faq_repo.update_faq(
        session,
        faq_id,
        **body.model_dump(exclude_none=True),
    )
    if not f:
        raise HTTPException(status_code=404, detail="FAQ 不存在")
    return {"id": f.id, "indexed": _sync_upsert(f)}


@router.delete("/faqs/{faq_id}")
def delete_faq(faq_id: int, session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    if not faq_repo.delete(session, faq_id):
        raise HTTPException(status_code=404, detail="FAQ 不存在")
    return {"deleted": True, "deindexed": _sync_delete(faq_id)}


@router.post("/documents", status_code=201)
async def upload_document(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
    session: Session = Depends(get_session),
    _=Depends(_check_admin_key),
):
    """上传文档切块入库：txt/md/docx/pdf → 抽文本 → 切块 → ai_faq(doc_knowledge) → 向量索引。

    同名文档重复上传 = 幂等替换（先删旧块再入新块）。抽取/切块失败返回 400 带
    中文原因（格式不支持、文件损坏、无有效文本），由 Java 代理透给管理员。
    索引同步沿用 FAQ 的口径：DB 先落（源），索引失败不回滚、只报 indexed=false，
    管理页的全量重建按钮随时可修。
    """
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="文件内容为空")
    if len(data) > _DOC_MAX_BYTES:
        raise HTTPException(status_code=400, detail="文档过大（上限 10MB）")
    raw_name = (file.filename or "").strip()
    display_title = (title or "").strip() or _doc_title_from_filename(raw_name)
    source = f"doc:{display_title}"
    try:
        text = doc_ingest.extract_text(raw_name, data)
    except doc_ingest.DocIngestError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    # 替换语义：先取旧块 id（供索引除名）→ 删旧行 → 切块入新行，一个事务提交
    old_ids = doc_ingest.delete_by_source(session, source)
    try:
        ids = doc_ingest.ingest_document(session, display_title, text, source)
    except doc_ingest.DocIngestError as e:
        session.rollback()  # 旧块已删未提交，回滚保住原数据
        raise HTTPException(status_code=400, detail=str(e)) from e
    session.commit()
    # 新块 upsert 进索引；被替换的旧块除名。索引失败不回滚 DB（MySQL 才是源）
    entries = [_faq_entry(f) for f in faq_repo.get_many(session, ids)]
    indexed_ok = _sync_upsert_entries(entries)
    if old_ids:
        try:
            _get_vectorstore().delete_ids(old_ids)
        except Exception:
            logger.exception("文档 %s 旧块索引除名失败，可全量重建修复", source)
    return {
        "name": display_title,
        "chunks": len(ids),
        "ids": ids,
        "replaced": len(old_ids),
        "indexed": indexed_ok,
        "model": settings.EMBEDDING_MODEL,
    }


@router.get("/documents")
def list_documents(session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    """已入库文档清单（按来源聚合块数），供管理页"文档知识"卡片展示"""
    return {"items": doc_ingest.list_documents(session)}


@router.delete("/documents")
def delete_document(
    name: str = Query(..., min_length=1, max_length=100),
    session: Session = Depends(get_session),
    _=Depends(_check_admin_key),
):
    """按文档名整批删除（DB 行 + 向量索引），返回删除块数"""
    source = f"doc:{name.strip()}"
    ids = doc_ingest.delete_by_source(session, source)
    if not ids:
        raise HTTPException(status_code=404, detail="文档不存在")
    session.commit()
    deindexed = True
    try:
        _get_vectorstore().delete_ids(ids)
    except Exception:
        logger.exception("文档 %s 索引除名失败，可全量重建修复", source)
        deindexed = False
    return {"deleted": len(ids), "deindexed": deindexed}


_DOC_MAX_BYTES = 10 * 1024 * 1024  # 上传体积上限：足够任何政策/手册，也防 multipart 吃爆内存


def _doc_title_from_filename(raw_name: str) -> str:
    stem = raw_name.rsplit(".", 1)[0] if "." in raw_name else raw_name
    stem = stem.strip()
    return stem[:60] or "未命名文档"


def _sync_upsert_entries(entries: list[dict]) -> bool:
    """批量把新入库条目同步进向量索引（DB 已提交，索引失败只报 false 不回滚）"""
    if not entries:
        return True
    try:
        _get_vectorstore().upsert_many(entries)
        return True
    except Exception:
        logger.exception("文档条目批量索引失败（%s 条），可全量重建修复", len(entries))
        return False


def _get_vectorstore() -> VectorStore:
    from app.main import get_vectorstore  # 延迟导入避免循环依赖

    return get_vectorstore()


def _get_product_store():
    from app.main import get_product_store  # 延迟导入避免循环依赖

    return get_product_store()


def _faq_entry(f) -> dict:
    return {
        "id": f.id,
        "question": f.question,
        "answer": f.answer,
        "category": f.category,
        "keywords": f.keywords or "",
    }


def _sync_upsert(f) -> bool:
    """新增/修改后增量同步向量库：启用条目 upsert，停用条目从索引删除。

    MySQL 是数据源（source of truth），索引失败不回滚 DB，仅标记 indexed=False，
    管理员可随后调用 POST /index 全量重建修复。
    """
    try:
        vs = _get_vectorstore()
        if f.status == 1:
            vs.upsert(_faq_entry(f))
        else:
            vs.delete_ids([f.id])
        return True
    except Exception:
        logger.exception("FAQ id=%s 增量索引同步失败，可调用 POST /v1/admin/kb/index 全量重建", f.id)
        return False


def _sync_delete(faq_id: int) -> bool:
    try:
        _get_vectorstore().delete_ids([faq_id])
        return True
    except Exception:
        logger.exception("FAQ id=%s 索引删除失败", faq_id)
        return False
