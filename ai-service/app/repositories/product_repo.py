"""product 仓储（只读）：商品向量召回索引的数据源查询"""
from sqlalchemy import select, text
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


def max_sales(session: Session) -> int:
    """全库在售商品的最大销量，作销量归一的固定基准。

    基准必须与"本次候选集"无关：按候选集内 max 归一时，同一件商品在首页 feed
    （全库候选）和导购召回（8 件候选）里拿到的销量分不同，排序随入口漂移。
    """
    value = session.execute(
        text("select coalesce(max(sales), 0) from product where status = 1 and deleted = 0")
    ).scalar()
    return int(value or 0)


def harvest_by_ids(session: Session, product_ids: list[int]) -> dict[int, tuple]:
    """商品 id → (harvest_date, shelf_life)，供新鲜度引擎打分（只取在售商品）"""
    if not product_ids:
        return {}
    rows = session.execute(
        select(Product.id, Product.harvest_date, Product.shelf_life).where(
            Product.id.in_(product_ids), Product.status == 1, Product.deleted == 0
        )
    ).all()
    return {int(r[0]): (r[1], r[2]) for r in rows}
