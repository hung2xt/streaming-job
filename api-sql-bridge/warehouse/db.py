"""DuckDB connection factory and schema bootstrapper."""
from pathlib import Path

import duckdb


def get_connection(db_path: Path) -> duckdb.DuckDBPyConnection:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = duckdb.connect(str(db_path))
    _bootstrap_schemas(conn)
    _apply_ddl(conn, db_path.parent / "schema.sql")
    return conn


def _bootstrap_schemas(conn: duckdb.DuckDBPyConnection) -> None:
    for schema in ("raw", "staging", "warehouse", "meta"):
        conn.execute(f"CREATE SCHEMA IF NOT EXISTS {schema}")
    conn.execute("CREATE SEQUENCE IF NOT EXISTS meta.run_id_seq START 1")
    conn.execute("CREATE SEQUENCE IF NOT EXISTS raw.response_id_seq START 1")


def _apply_ddl(conn: duckdb.DuckDBPyConnection, schema_file: Path) -> None:
    sql = schema_file.read_text()
    # Execute each statement separately
    for statement in sql.split(";"):
        # Strip comment-only lines, keep any line that contains actual SQL
        lines = [ln for ln in statement.splitlines() if ln.strip() and not ln.strip().startswith("--")]
        stmt = "\n".join(lines).strip()
        if stmt:
            try:
                conn.execute(stmt)
            except Exception:
                pass  # IF NOT EXISTS handles most; silently skip truly invalid fragments
