"""
Gateway API  —  port 8000
Control plane for the REST API → SQL Bridge.
  POST /ingest          trigger ETL pipeline
  GET  /runs            ingestion run history
  GET  /runs/{id}       single run details
  GET  /query           run ad-hoc SQL against the warehouse
  GET  /schema          list all tables and row counts
  GET  /analytics       pre-built analytical queries
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))

from fastapi import FastAPI, HTTPException, Query as QParam
from pydantic import BaseModel
import duckdb

from config import WAREHOUSE_PATH
from warehouse.db import get_connection
from orchestrator import run_pipeline

app = FastAPI(title="API→SQL Bridge Gateway", version="1.0")


def _get_conn() -> duckdb.DuckDBPyConnection:
    return get_connection(WAREHOUSE_PATH)


# ── INGEST ──────────────────────────────────────────────────

class IngestRequest(BaseModel):
    endpoints: list[str] = ["customers", "products", "orders"]


@app.post("/ingest", summary="Trigger the ETL pipeline")
def ingest(req: IngestRequest | None = None):
    endpoints = req.endpoints if req else ["customers", "products", "orders"]
    valid = {"customers", "products", "orders"}
    bad = [e for e in endpoints if e not in valid]
    if bad:
        raise HTTPException(400, f"Unknown endpoints: {bad}. Valid: {sorted(valid)}")
    return run_pipeline(endpoints)


# ── RUNS ─────────────────────────────────────────────────────

@app.get("/runs", summary="List ingestion run history")
def list_runs(limit: int = QParam(20, ge=1, le=100)):
    conn = _get_conn()
    rows = conn.execute(f"""
        SELECT run_id, started_at, finished_at, status, endpoints,
               rows_extracted, rows_loaded, error_message
        FROM meta.ingestion_runs
        ORDER BY run_id DESC
        LIMIT {limit}
    """).fetchdf().to_dict(orient="records")
    conn.close()
    return {"runs": rows}


@app.get("/runs/{run_id}", summary="Get a single ingestion run")
def get_run(run_id: int):
    conn = _get_conn()
    row = conn.execute(
        "SELECT * FROM meta.ingestion_runs WHERE run_id = ?", [run_id]
    ).fetchdf().to_dict(orient="records")
    conn.close()
    if not row:
        raise HTTPException(404, f"Run {run_id} not found")
    return row[0]


# ── QUERY ────────────────────────────────────────────────────

@app.get("/query", summary="Run a read-only SQL query against the warehouse")
def run_query(sql: str = QParam(..., description="SELECT statement to run")):
    sql_lower = sql.strip().lower()
    if not sql_lower.startswith("select"):
        raise HTTPException(400, "Only SELECT statements are allowed")
    conn = _get_conn()
    try:
        result = conn.execute(sql).fetchdf()
        conn.close()
        return {"columns": list(result.columns), "rows": result.to_dict(orient="records"), "count": len(result)}
    except Exception as e:
        conn.close()
        raise HTTPException(400, str(e))


# ── SCHEMA ───────────────────────────────────────────────────

@app.get("/schema", summary="List all warehouse tables with row counts")
def get_schema():
    conn = _get_conn()
    tables = conn.execute("""
        SELECT table_schema, table_name
        FROM information_schema.tables
        WHERE table_schema IN ('raw','staging','warehouse','meta')
        ORDER BY table_schema, table_name
    """).fetchall()

    result = {}
    for schema, table in tables:
        count = conn.execute(f"SELECT COUNT(*) FROM {schema}.{table}").fetchone()[0]
        result.setdefault(schema, []).append({"table": table, "rows": count})

    conn.close()
    return result


# ── ANALYTICS ────────────────────────────────────────────────

@app.get("/analytics/revenue-by-month", summary="Monthly revenue trend")
def revenue_by_month():
    conn = _get_conn()
    df = conn.execute("""
        SELECT
            d.year,
            d.month,
            d.month_name,
            COUNT(DISTINCT f.order_id)   AS orders,
            SUM(f.total_amount)          AS revenue,
            AVG(f.total_amount)          AS avg_order_value
        FROM warehouse.fact_orders f
        JOIN warehouse.dim_date d ON d.date_key = f.date_key
        WHERE f.status != 'cancelled'
        GROUP BY d.year, d.month, d.month_name
        ORDER BY d.year, d.month
    """).fetchdf()
    conn.close()
    return df.to_dict(orient="records")


@app.get("/analytics/top-products", summary="Top products by revenue")
def top_products(limit: int = QParam(10, ge=1, le=50)):
    conn = _get_conn()
    df = conn.execute(f"""
        SELECT
            p.sku,
            p.name,
            p.category,
            SUM(fi.qty)         AS total_units_sold,
            SUM(fi.subtotal)    AS total_revenue
        FROM warehouse.fact_order_items fi
        JOIN warehouse.dim_products p ON p.product_key = fi.product_key
        GROUP BY p.sku, p.name, p.category
        ORDER BY total_revenue DESC
        LIMIT {limit}
    """).fetchdf()
    conn.close()
    return df.to_dict(orient="records")


@app.get("/analytics/customer-segments", summary="Revenue breakdown by customer segment")
def customer_segments():
    conn = _get_conn()
    df = conn.execute("""
        SELECT
            c.segment,
            COUNT(DISTINCT c.customer_key)  AS customer_count,
            COUNT(DISTINCT f.order_id)      AS order_count,
            SUM(f.total_amount)             AS total_revenue,
            AVG(f.total_amount)             AS avg_order_value
        FROM warehouse.fact_orders f
        JOIN warehouse.dim_customers c ON c.customer_key = f.customer_key
        WHERE f.status != 'cancelled'
        GROUP BY c.segment
        ORDER BY total_revenue DESC
    """).fetchdf()
    conn.close()
    return df.to_dict(orient="records")


@app.get("/analytics/order-status", summary="Order status funnel")
def order_status():
    conn = _get_conn()
    df = conn.execute("""
        SELECT
            status,
            COUNT(*)                    AS orders,
            SUM(total_amount)           AS total_value,
            ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS pct
        FROM warehouse.fact_orders
        GROUP BY status
        ORDER BY orders DESC
    """).fetchdf()
    conn.close()
    return df.to_dict(orient="records")


@app.get("/health")
def health():
    try:
        conn = _get_conn()
        fact_count = conn.execute("SELECT COUNT(*) FROM warehouse.fact_orders").fetchone()[0]
        conn.close()
        return {"status": "ok", "warehouse_orders": fact_count}
    except Exception as e:
        return {"status": "error", "detail": str(e)}
