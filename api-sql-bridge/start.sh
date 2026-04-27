#!/usr/bin/env bash
# Starts both services in background with logs written to ./logs/
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

cd "$PROJECT_DIR"

echo "Starting Mock Source API  (http://127.0.0.1:8001) ..."
uvicorn mock_source_api.main:app --host 127.0.0.1 --port 8001 --reload \
    > "$LOG_DIR/source_api.log" 2>&1 &
SOURCE_PID=$!

sleep 1

echo "Starting Gateway API       (http://127.0.0.1:8000) ..."
uvicorn gateway.main:app --host 127.0.0.1 --port 8000 --reload \
    > "$LOG_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!

echo ""
echo "Both services running."
echo "  Source API  PID=$SOURCE_PID  → http://127.0.0.1:8001/docs"
echo "  Gateway     PID=$GATEWAY_PID  → http://127.0.0.1:8000/docs"
echo ""
echo "Quick-start:"
echo "  1. Trigger ETL:  curl -X POST http://127.0.0.1:8000/ingest"
echo "  2. Check schema: curl http://127.0.0.1:8000/schema"
echo "  3. Analytics:    curl http://127.0.0.1:8000/analytics/revenue-by-month"
echo ""
echo "Stop with:  kill $SOURCE_PID $GATEWAY_PID"
echo "Or:         pkill -f 'uvicorn mock_source_api\|uvicorn gateway'"
