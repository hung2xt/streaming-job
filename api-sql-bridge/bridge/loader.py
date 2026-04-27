"""
Loader  —  Bridge Layer 3
Writes transformed DataFrames into DuckDB.
Staging: UPSERT (INSERT OR REPLACE) — idempotent reruns.
Warehouse: full dimensional model build with surrogate keys.
"""
import logging

import duckdb
import pandas as pd

log = logging.getLogger(__name__)


class WarehouseLoader:
    def __init__(self, conn: duckdb.DuckDBPyConnection):
        self.conn = conn

    # ── STAGING ──────────────────────────────────────────────

    def load_staging_customers(self, df: pd.DataFrame) -> int:
        if df.empty:
            return 0
        self.conn.execute("DELETE FROM staging.customers WHERE id IN (SELECT id FROM df)")
        self.conn.execute("INSERT INTO staging.customers SELECT *, now() FROM df")
        log.info("[LOAD] staging.customers: %d rows", len(df))
        return len(df)

    def load_staging_products(self, df: pd.DataFrame) -> int:
        if df.empty:
            return 0
        self.conn.execute("DELETE FROM staging.products WHERE id IN (SELECT id FROM df)")
        self.conn.execute("INSERT INTO staging.products SELECT *, now() FROM df")
        log.info("[LOAD] staging.products: %d rows", len(df))
        return len(df)

    def load_staging_orders(self, orders_df: pd.DataFrame, items_df: pd.DataFrame) -> int:
        if orders_df.empty:
            return 0
        self.conn.execute("DELETE FROM staging.orders WHERE id IN (SELECT id FROM orders_df)")
        self.conn.execute(
            "INSERT INTO staging.orders SELECT id, customer_id, status, total_amount, currency, ordered_at, updated_at, now() FROM orders_df"
        )
        if not items_df.empty:
            self.conn.execute(
                "DELETE FROM staging.order_items WHERE order_id IN (SELECT DISTINCT order_id FROM items_df)"
            )
            self.conn.execute(
                "INSERT INTO staging.order_items SELECT order_id, product_id, sku, qty, unit_price, subtotal, now() FROM items_df"
            )
        log.info("[LOAD] staging.orders: %d rows, staging.order_items: %d rows", len(orders_df), len(items_df))
        return len(orders_df)

    # ── DIMENSIONAL MODEL ─────────────────────────────────────

    def load_dim_date(self, df: pd.DataFrame) -> int:
        existing = self.conn.execute("SELECT COUNT(*) FROM warehouse.dim_date").fetchone()[0]
        if existing > 0:
            log.info("[LOAD] dim_date already populated (%d rows), skipping", existing)
            return 0
        self.conn.execute("INSERT INTO warehouse.dim_date SELECT * FROM df")
        log.info("[LOAD] warehouse.dim_date: %d rows", len(df))
        return len(df)

    def load_dim_customers(self) -> int:
        self.conn.execute("""
            DELETE FROM warehouse.dim_customers
            WHERE customer_id IN (SELECT id FROM staging.customers)
        """)
        self.conn.execute("""
            INSERT INTO warehouse.dim_customers
            SELECT id, id, name, email, country, city, segment, created_at, now(), NULL
            FROM staging.customers
        """)
        n = self.conn.execute("SELECT COUNT(*) FROM warehouse.dim_customers").fetchone()[0]
        log.info("[LOAD] warehouse.dim_customers: %d total rows", n)
        return n

    def load_dim_products(self) -> int:
        self.conn.execute("""
            DELETE FROM warehouse.dim_products
            WHERE product_id IN (SELECT id FROM staging.products)
        """)
        self.conn.execute("""
            INSERT INTO warehouse.dim_products
            SELECT id, id, sku, name, category, price, is_active, now(), NULL
            FROM staging.products
        """)
        n = self.conn.execute("SELECT COUNT(*) FROM warehouse.dim_products").fetchone()[0]
        log.info("[LOAD] warehouse.dim_products: %d total rows", n)
        return n

    def load_fact_orders(self) -> int:
        """Join staging → dimensions to produce fact_orders with surrogate keys."""
        self.conn.execute("""
            DELETE FROM warehouse.fact_orders
            WHERE order_id IN (SELECT id FROM staging.orders)
        """)
        self.conn.execute("""
            INSERT INTO warehouse.fact_orders
            SELECT
                o.id                                          AS order_key,
                o.id                                          AS order_id,
                dc.customer_key,
                CAST(strftime(o.ordered_at, '%Y%m%d') AS INTEGER)  AS date_key,
                o.status,
                o.total_amount,
                o.currency,
                COUNT(oi.product_id)                          AS item_count,
                o.ordered_at,
                o.updated_at
            FROM staging.orders o
            JOIN warehouse.dim_customers dc ON dc.customer_id = o.customer_id
            LEFT JOIN staging.order_items oi ON oi.order_id = o.id
            GROUP BY o.id, o.customer_id, dc.customer_key, o.status,
                     o.total_amount, o.currency, o.ordered_at, o.updated_at
        """)
        n = self.conn.execute("SELECT COUNT(*) FROM warehouse.fact_orders").fetchone()[0]
        log.info("[LOAD] warehouse.fact_orders: %d total rows", n)
        return n

    def load_fact_order_items(self) -> int:
        self.conn.execute("""
            DELETE FROM warehouse.fact_order_items
            WHERE order_id IN (SELECT id FROM staging.orders)
        """)
        self.conn.execute("""
            INSERT INTO warehouse.fact_order_items
            SELECT
                ROW_NUMBER() OVER (ORDER BY oi.order_id, oi.product_id) AS item_key,
                oi.order_id,
                dp.product_key,
                dc.customer_key,
                CAST(strftime(o.ordered_at, '%Y%m%d') AS INTEGER)       AS date_key,
                oi.qty,
                oi.unit_price,
                oi.subtotal
            FROM staging.order_items oi
            JOIN staging.orders o ON o.id = oi.order_id
            JOIN warehouse.dim_customers dc ON dc.customer_id = o.customer_id
            JOIN warehouse.dim_products  dp ON dp.product_id  = oi.product_id
        """)
        n = self.conn.execute("SELECT COUNT(*) FROM warehouse.fact_order_items").fetchone()[0]
        log.info("[LOAD] warehouse.fact_order_items: %d total rows", n)
        return n
