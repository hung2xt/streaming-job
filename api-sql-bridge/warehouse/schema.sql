-- =============================================================
-- REST API → SQL Bridge  |  Warehouse Schema (DuckDB)
-- =============================================================
-- Layer 1: RAW (landing zone — exact API response stored as JSON)
-- Layer 2: STAGING (parsed, typed, deduplicated)
-- Layer 3: DIMENSIONAL MODEL (star schema for analytics)
-- =============================================================

-- ── RAW LAYER ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS raw.api_responses (
    id            INTEGER DEFAULT nextval('raw.response_id_seq') PRIMARY KEY,
    endpoint      VARCHAR NOT NULL,
    fetched_at    TIMESTAMP DEFAULT now(),
    http_status   INTEGER,
    payload       JSON,       -- full API response blob
    page          INTEGER,
    page_size     INTEGER,
    record_count  INTEGER
);

-- ── STAGING LAYER ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staging.customers (
    id          INTEGER PRIMARY KEY,
    name        VARCHAR,
    email       VARCHAR,
    country     VARCHAR(3),
    city        VARCHAR,
    segment     VARCHAR,
    created_at  TIMESTAMP,
    _ingested_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS staging.products (
    id          INTEGER PRIMARY KEY,
    sku         VARCHAR UNIQUE,
    name        VARCHAR,
    category    VARCHAR,
    price       DECIMAL(10, 2),
    stock_qty   INTEGER,
    is_active   BOOLEAN,
    _ingested_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS staging.orders (
    id           INTEGER PRIMARY KEY,
    customer_id  INTEGER,
    status       VARCHAR,
    total_amount DECIMAL(12, 2),
    currency     VARCHAR(3),
    ordered_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    _ingested_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS staging.order_items (
    order_id    INTEGER,
    product_id  INTEGER,
    sku         VARCHAR,
    qty         INTEGER,
    unit_price  DECIMAL(10, 2),
    subtotal    DECIMAL(12, 2),
    _ingested_at TIMESTAMP DEFAULT now(),
    PRIMARY KEY (order_id, product_id)
);

-- ── DIMENSIONAL MODEL (Star Schema) ──────────────────────────
CREATE TABLE IF NOT EXISTS warehouse.dim_customers (
    customer_key  INTEGER PRIMARY KEY,   -- surrogate key
    customer_id   INTEGER UNIQUE,        -- natural key from source
    name          VARCHAR,
    email         VARCHAR,
    country       VARCHAR(3),
    city          VARCHAR,
    segment       VARCHAR,
    created_at    TIMESTAMP,
    _valid_from   TIMESTAMP DEFAULT now(),
    _valid_to     TIMESTAMP             -- NULL = current record (SCD Type 2 ready)
);

CREATE TABLE IF NOT EXISTS warehouse.dim_products (
    product_key  INTEGER PRIMARY KEY,
    product_id   INTEGER UNIQUE,
    sku          VARCHAR,
    name         VARCHAR,
    category     VARCHAR,
    price        DECIMAL(10, 2),
    is_active    BOOLEAN,
    _valid_from  TIMESTAMP DEFAULT now(),
    _valid_to    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS warehouse.dim_date (
    date_key    INTEGER PRIMARY KEY,    -- YYYYMMDD integer
    full_date   DATE,
    year        INTEGER,
    quarter     INTEGER,
    month       INTEGER,
    month_name  VARCHAR,
    week        INTEGER,
    day_of_week INTEGER,
    day_name    VARCHAR,
    is_weekend  BOOLEAN
);

CREATE TABLE IF NOT EXISTS warehouse.fact_orders (
    order_key      INTEGER PRIMARY KEY,
    order_id       INTEGER UNIQUE,
    customer_key   INTEGER REFERENCES warehouse.dim_customers(customer_key),
    date_key       INTEGER REFERENCES warehouse.dim_date(date_key),
    status         VARCHAR,
    total_amount   DECIMAL(12, 2),
    currency       VARCHAR(3),
    item_count     INTEGER,
    ordered_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS warehouse.fact_order_items (
    item_key      INTEGER PRIMARY KEY,
    order_id      INTEGER,
    product_key   INTEGER REFERENCES warehouse.dim_products(product_key),
    customer_key  INTEGER REFERENCES warehouse.dim_customers(customer_key),
    date_key      INTEGER REFERENCES warehouse.dim_date(date_key),
    qty           INTEGER,
    unit_price    DECIMAL(10, 2),
    subtotal      DECIMAL(12, 2)
);

-- ── METADATA ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS meta.ingestion_runs (
    run_id       INTEGER DEFAULT nextval('meta.run_id_seq') PRIMARY KEY,
    started_at   TIMESTAMP DEFAULT now(),
    finished_at  TIMESTAMP,
    status       VARCHAR,    -- 'running' | 'success' | 'failed'
    endpoints    VARCHAR,
    rows_extracted  INTEGER DEFAULT 0,
    rows_loaded     INTEGER DEFAULT 0,
    error_message   VARCHAR
);
