"""product 仓储（只读）：商品向量召回索引的数据源查询"""
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.product import Product, ProductCategory


def list_on_sale(session: Session) -> list[Product]:
    """在售商品（status=1 且未软删），按 id 升序保证重建索引结果稳定"""
    return list(
        session.scalars(
            select(Product)
            .where(Product.status == 1, Product.deleted == 0)
            .order_by(Product.id)
        )
    )


def category_name_map(session: Session) -> dict[int, str]:
    """分类 id → 名称（含禁用分类，索引只做名称拼接用）"""
    return {c.id: c.name for c in session.scalars(select(ProductCategory))}


def list_by_ids(session: Session, product_ids: list[int]) -> list[Product]:
    """按 id 取在售商品（status=1 且未软删），用于语义搜索命中集的重排打分"""
    if not product_ids:
        return []
    return list(
        session.scalars(
            select(Product).where(
                Product.id.in_(product_ids), Product.status == 1, Product.deleted == 0
            )
        )
    )


def category_by_ids(session: Session, product_ids: list[int]) -> dict[int, int]:
    """商品 id → 分类 id（个性化重排查偏好权重用；软删/下架也返回，过滤归 Java）"""
    if not product_ids:
        return {}
    rows = session.execute(
        select(Product.id, Product.category_id).where(Product.id.in_(product_ids))
    ).all()
    return {int(r[0]): int(r[1]) for r in rows}
