"""product / product_category 只读 ORM 模型（商品向量召回索引数据源）

仅映射索引构建所需列；ai-service 对商品表只读不写，写操作归 Java 主系统。
"""
from datetime import date, datetime

from sqlalchemy import BigInteger, Date, DateTime, Integer, Numeric, SmallInteger, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class Product(Base):
    __tablename__ = "product"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    subtitle: Mapped[str | None] = mapped_column(String(200))
    category_id: Mapped[int] = mapped_column(BigInteger)
    price: Mapped[float] = mapped_column(Numeric(10, 2))
    stock: Mapped[int] = mapped_column(Integer)
    sales: Mapped[int] = mapped_column(Integer)
    origin: Mapped[str | None] = mapped_column(String(100))
    harvest_date: Mapped[date | None] = mapped_column(Date)
    shelf_life: Mapped[str | None] = mapped_column(String(50))
    storage: Mapped[str | None] = mapped_column(String(50))
    status: Mapped[int] = mapped_column(SmallInteger, default=0)
    create_time: Mapped[datetime | None] = mapped_column(DateTime)
    deleted: Mapped[int] = mapped_column(SmallInteger, default=0)


class ProductCategory(Base):
    __tablename__ = "product_category"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    name: Mapped[str] = mapped_column(String(50))
    parent_id: Mapped[int] = mapped_column(BigInteger, default=0)
    status: Mapped[int] = mapped_column(SmallInteger, default=1)
    deleted: Mapped[int] = mapped_column(SmallInteger, default=0)
