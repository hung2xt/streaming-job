"""
Mock Source API  — port 8001
Simulates an e-commerce operational database exposed as a REST API.
This is what a real SaaS platform (Shopify, Stripe, etc.) looks like.
"""
from fastapi import FastAPI, Query
from .data import CUSTOMERS, PRODUCTS, ORDERS

app = FastAPI(title="Mock E-Commerce API", version="1.0")


def _paginate(items: list, page: int, page_size: int) -> dict:
    total = len(items)
    start = (page - 1) * page_size
    end = start + page_size
    return {
        "page": page,
        "page_size": page_size,
        "total": total,
        "total_pages": (total + page_size - 1) // page_size,
        "data": items[start:end],
    }


@app.get("/customers")
def list_customers(page: int = Query(1, ge=1), page_size: int = Query(50, ge=1, le=200)):
    return _paginate(CUSTOMERS, page, page_size)


@app.get("/customers/{customer_id}")
def get_customer(customer_id: int):
    for c in CUSTOMERS:
        if c["id"] == customer_id:
            return c
    return {"error": "not found"}, 404


@app.get("/products")
def list_products(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    category: str | None = None,
    active_only: bool = False,
):
    items = PRODUCTS
    if category:
        items = [p for p in items if p["category"] == category]
    if active_only:
        items = [p for p in items if p["is_active"]]
    return _paginate(items, page, page_size)


@app.get("/orders")
def list_orders(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    status: str | None = None,
    customer_id: int | None = None,
):
    items = ORDERS
    if status:
        items = [o for o in items if o["status"] == status]
    if customer_id:
        items = [o for o in items if o["customer_id"] == customer_id]
    return _paginate(items, page, page_size)


@app.get("/health")
def health():
    return {"status": "ok", "records": {"customers": len(CUSTOMERS), "products": len(PRODUCTS), "orders": len(ORDERS)}}
