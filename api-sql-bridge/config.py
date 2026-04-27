from pathlib import Path

BASE_DIR = Path(__file__).parent

# Source API (mock)
SOURCE_API_HOST = "127.0.0.1"
SOURCE_API_PORT = 8001
SOURCE_API_URL = f"http://{SOURCE_API_HOST}:{SOURCE_API_PORT}"

# Gateway API
GATEWAY_HOST = "127.0.0.1"
GATEWAY_PORT = 8000

# Warehouse
WAREHOUSE_PATH = BASE_DIR / "warehouse" / "ecommerce.duckdb"

# ETL
BATCH_SIZE = 100
API_ENDPOINTS = {
    "customers": f"{SOURCE_API_URL}/customers",
    "products":  f"{SOURCE_API_URL}/products",
    "orders":    f"{SOURCE_API_URL}/orders",
}
