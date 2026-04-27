"""Generates deterministic fake e-commerce data for the mock API."""
import random
from datetime import datetime, timedelta
from faker import Faker

fake = Faker()
Faker.seed(42)
random.seed(42)

CATEGORIES = ["Electronics", "Clothing", "Books", "Home & Garden", "Sports", "Food & Beverage"]
STATUSES = ["pending", "processing", "shipped", "delivered", "cancelled"]

def _make_customers(n: int = 200) -> list[dict]:
    customers = []
    base_dt = datetime(2023, 1, 1)
    for i in range(1, n + 1):
        customers.append({
            "id": i,
            "name": fake.name(),
            "email": fake.unique.email(),
            "country": fake.country_code(),
            "city": fake.city(),
            "segment": random.choice(["B2C", "B2B", "VIP"]),
            "created_at": (base_dt + timedelta(days=random.randint(0, 400))).isoformat(),
        })
    return customers

def _make_products(n: int = 80) -> list[dict]:
    products = []
    for i in range(1, n + 1):
        cat = random.choice(CATEGORIES)
        products.append({
            "id": i,
            "sku": f"SKU-{i:05d}",
            "name": fake.catch_phrase(),
            "category": cat,
            "price": round(random.uniform(4.99, 999.99), 2),
            "stock_qty": random.randint(0, 500),
            "is_active": random.random() > 0.1,
        })
    return products

def _make_orders(customers: list, products: list, n: int = 500) -> list[dict]:
    orders = []
    base_dt = datetime(2023, 6, 1)
    for i in range(1, n + 1):
        customer = random.choice(customers)
        num_items = random.randint(1, 5)
        items = []
        total = 0.0
        for _ in range(num_items):
            product = random.choice(products)
            qty = random.randint(1, 4)
            subtotal = round(product["price"] * qty, 2)
            total += subtotal
            items.append({
                "product_id": product["id"],
                "sku": product["sku"],
                "qty": qty,
                "unit_price": product["price"],
                "subtotal": subtotal,
            })
        order_dt = base_dt + timedelta(days=random.randint(0, 300), hours=random.randint(0, 23))
        orders.append({
            "id": i,
            "customer_id": customer["id"],
            "status": random.choice(STATUSES),
            "total_amount": round(total, 2),
            "currency": "USD",
            "items": items,
            "ordered_at": order_dt.isoformat(),
            "updated_at": (order_dt + timedelta(hours=random.randint(1, 72))).isoformat(),
        })
    return orders


# Build once at import time
CUSTOMERS = _make_customers()
PRODUCTS  = _make_products()
ORDERS    = _make_orders(CUSTOMERS, PRODUCTS)
