"""知识库管理接口（阶段二）

- GET  /v1/admin/kb/stats   知识库状态
- POST /v1/admin/kb/index   触发全量重建（读 MySQL → bge-m3 向量化 → 写 Chroma）
- GET  /v1/admin/kb/faqs    FAQ 列表（分页/分类）
- POST /v1/admin/kb/faqs    新增 FAQ
- PUT  /v1/admin/kb/faqs/{id} 更新 FAQ
- DELETE /v1/admin/kb/faqs/{id} 删除 FAQ

提示：修改 FAQ 后需调用 POST /index 重建索引才生效。
"""
from fastapi import APIRouter, Depends, Header, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.config import settings
from app.db import get_session
from app.rag.vectorstore import VectorStore
from app.repositories import faq_repo

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


def _check_admin_key(x_admin_key: str = Header(default="")) -> None:
    """服务间鉴权：配置了 JAVA_INTERNAL_KEY 后必须携带匹配的 Key（阶段四对接管理端）"""
    if settings.JAVA_INTERNAL_KEY and x_admin_key != settings.JAVA_INTERNAL_KEY:
        raise HTTPException(status_code=401, detail="无效的访问密钥")


@router.get("/stats")
def stats(session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    return {
        "faq_count": faq_repo.count_enabled(session),
        "indexed_count": _get_vectorstore().count(),
        "embedding_model": settings.EMBEDDING_MODEL,
        "vector_db_path": settings.VECTOR_DB_PATH,
        "top_k": settings.KB_TOP_K,
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
    return {"id": f.id}


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
    return {"id": f.id}


@router.delete("/faqs/{faq_id}")
def delete_faq(faq_id: int, session: Session = Depends(get_session), _=Depends(_check_admin_key)):
    if not faq_repo.delete(session, faq_id):
        raise HTTPException(status_code=404, detail="FAQ 不存在")
    return {"deleted": True}


def _get_vectorstore() -> VectorStore:
    from app.main import get_vectorstore  # 延迟导入避免循环依赖

    return get_vectorstore()
