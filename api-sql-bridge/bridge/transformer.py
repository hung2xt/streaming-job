"""
Transformer  —  Bridge Layer 2
Converts raw API JSON records into typed, cleaned DataFrames ready for SQL load.
Handles nested structures (e.g., order → order_items), type coercion, deduplication.
"""
import logging
from datetime import datetime

import pandas as pd

log = logging.getLogger(__name__)


def transform_customers(records: list[dict]) -> pd.DataFrame:
    if not records:
        return pd.DataFrame()

    df = pd.DataFrame(records)
    df["created_at"] = pd.to_datetime(df["created_at"])
    df["name"] = df["name"].str.strip()
    df["email"] = df["email"].str.lower().str.strip()
    df["country"] = df["country"].str.upper().str[:3]
    df["segment"] = df["segment"].str.upper()

    df = df.drop_duplicates(subset=["id"])
    log.info("[TRANSFORM] customers: %d rows", len(df))
    return df[["id", "name", "email", "country", "city", "segment", "created_at"]]


def transform_products(records: list[dict]) -> pd.DataFrame:
    if not records:
        return pd.DataFrame()

    df = pd.DataFrame(records)
    df["price"] = pd.to_numeric(df["price"], errors="coerce").fillna(0.0)
    df["stock_qty"] = pd.to_numeric(df["stock_qty"], errors="coerce").fillna(0).astype(int)
    df["is_active"] = df["is_active"].astype(bool)
    df["sku"] = df["sku"].str.strip().str.upper()
    df["name"] = df["name"].str.strip()

    df = df.drop_duplicates(subset=["id"])
    log.info("[TRANSFORM] products: %d rows", len(df))
    return df[["id", "sku", "name", "category", "price", "stock_qty", "is_active"]]


def transform_orders(records: list[dict]) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Returns (orders_df, order_items_df) — explodes nested items array."""
    if not records:
        return pd.DataFrame(), pd.DataFrame()

    orders_rows = []
    items_rows = []

    for rec in records:
        items = rec.get("items", [])
        orders_rows.append({
            "id":           rec["id"],
            "customer_id":  rec["customer_id"],
            "status":       rec["status"],
            "total_amount": float(rec["total_amount"]),
            "currency":     rec.get("currency", "USD"),
            "ordered_at":   rec["ordered_at"],
            "updated_at":   rec["updated_at"],
            "item_count":   len(items),
        })
        for item in items:
            items_rows.append({
                "order_id":   rec["id"],
                "product_id": item["product_id"],
                "sku":        item["sku"],
                "qty":        int(item["qty"]),
                "unit_price": float(item["unit_price"]),
                "subtotal":   float(item["subtotal"]),
            })

    orders_df = pd.DataFrame(orders_rows)
    orders_df["ordered_at"] = pd.to_datetime(orders_df["ordered_at"])
    orders_df["updated_at"] = pd.to_datetime(orders_df["updated_at"])
    orders_df["status"] = orders_df["status"].str.lower()
    orders_df = orders_df.drop_duplicates(subset=["id"])

    items_df = pd.DataFrame(items_rows).drop_duplicates(subset=["order_id", "product_id"])

    log.info("[TRANSFORM] orders: %d rows, order_items: %d rows", len(orders_df), len(items_df))
    return orders_df, items_df


def build_dim_date(start: str = "2023-01-01", end: str = "2025-12-31") -> pd.DataFrame:
    """Generates a complete date dimension table."""
    dates = pd.date_range(start=start, end=end, freq="D")
    df = pd.DataFrame({"full_date": dates})
    df["date_key"]    = df["full_date"].dt.strftime("%Y%m%d").astype("int32")
    df["year"]        = df["full_date"].dt.year
    df["quarter"]     = df["full_date"].dt.quarter
    df["month"]       = df["full_date"].dt.month
    df["month_name"]  = df["full_date"].dt.strftime("%B")
    df["week"]        = df["full_date"].dt.isocalendar().week.astype(int)
    df["day_of_week"] = df["full_date"].dt.dayofweek    # 0=Monday
    df["day_name"]    = df["full_date"].dt.strftime("%A")
    df["is_weekend"]  = df["day_of_week"].isin([5, 6])
    # Reorder to match DDL column order: date_key first, then full_date
    df = df[["date_key", "full_date", "year", "quarter", "month", "month_name", "week", "day_of_week", "day_name", "is_weekend"]]
    df["full_date"] = df["full_date"].dt.date  # DATE type, not TIMESTAMP
    log.info("[TRANSFORM] dim_date: %d rows", len(df))
    return df
