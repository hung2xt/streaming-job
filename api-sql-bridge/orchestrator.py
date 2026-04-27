"""
Orchestrator  —  wires Extract → Transform → Load together.
Run directly:  python orchestrator.py
Or trigger via the Gateway API: POST /ingest
"""
import logging
import time
from datetime import datetime
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).parent))

from config import SOURCE_API_URL, WAREHOUSE_PATH
from bridge.extractor import APIExtractor
from bridge.transformer import (
    transform_customers, transform_products, transform_orders, build_dim_date
)
from bridge.loader import WarehouseLoader
from warehouse.db import get_connection

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("orchestrator")


def run_pipeline(endpoints: list[str] | None = None) -> dict:
    endpoints = endpoints or ["customers", "products", "orders"]
    started_at = datetime.now()
    stats = {"rows_extracted": 0, "rows_loaded": 0, "status": "running"}

    conn = get_connection(WAREHOUSE_PATH)
    extractor = APIExtractor(conn, SOURCE_API_URL, page_size=100)
    loader = WarehouseLoader(conn)

    # Seed date dimension once
    dim_date_df = build_dim_date()
    loader.load_dim_date(dim_date_df)

    run_id = conn.execute("""
        INSERT INTO meta.ingestion_runs (started_at, status, endpoints)
        VALUES (now(), 'running', ?)
        RETURNING run_id
    """, [",".join(endpoints)]).fetchone()[0]

    try:
        rows_ext = rows_load = 0

        if "customers" in endpoints:
            raw = extractor.extract("customers")
            rows_ext += len(raw)
            df = transform_customers(raw)
            rows_load += loader.load_staging_customers(df)
            rows_load += loader.load_dim_customers()

        if "products" in endpoints:
            raw = extractor.extract("products")
            rows_ext += len(raw)
            df = transform_products(raw)
            rows_load += loader.load_staging_products(df)
            rows_load += loader.load_dim_products()

        if "orders" in endpoints:
            raw = extractor.extract("orders")
            rows_ext += len(raw)
            orders_df, items_df = transform_orders(raw)
            rows_load += loader.load_staging_orders(orders_df, items_df)
            rows_load += loader.load_fact_orders()
            rows_load += loader.load_fact_order_items()

        duration = round((datetime.now() - started_at).total_seconds(), 2)
        conn.execute("""
            UPDATE meta.ingestion_runs
            SET finished_at = now(), status = 'success', rows_extracted = ?, rows_loaded = ?
            WHERE run_id = ?
        """, [rows_ext, rows_load, run_id])

        stats = {
            "run_id": run_id,
            "status": "success",
            "duration_seconds": duration,
            "rows_extracted": rows_ext,
            "rows_loaded": rows_load,
            "endpoints": endpoints,
        }
        log.info("Pipeline complete in %.1fs  |  extracted=%d  loaded=%d", duration, rows_ext, rows_load)

    except Exception as e:
        conn.execute("""
            UPDATE meta.ingestion_runs
            SET finished_at = now(), status = 'failed', error_message = ?
            WHERE run_id = ?
        """, [str(e), run_id])
        stats["status"] = "failed"
        stats["error"] = str(e)
        log.error("Pipeline failed: %s", e, exc_info=True)

    finally:
        conn.close()

    return stats


if __name__ == "__main__":
    result = run_pipeline()
    print("\n── Pipeline Result ──────────────────────────")
    for k, v in result.items():
        print(f"  {k:<20} {v}")
