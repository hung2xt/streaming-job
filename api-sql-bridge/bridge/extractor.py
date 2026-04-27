"""
Extractor  —  Bridge Layer 1
Fetches paginated JSON from REST API endpoints and yields raw records.
Handles pagination, retries, and stores raw blobs in raw.api_responses.
"""
import json
import logging
import time
from datetime import datetime

import httpx

log = logging.getLogger(__name__)


class APIExtractor:
    def __init__(self, db_conn, base_url: str, page_size: int = 100, max_retries: int = 3):
        self.db = db_conn
        self.base_url = base_url.rstrip("/")
        self.page_size = page_size
        self.max_retries = max_retries

    def _fetch_page(self, client: httpx.Client, endpoint: str, page: int) -> dict:
        url = f"{self.base_url}/{endpoint}"
        params = {"page": page, "page_size": self.page_size}
        for attempt in range(1, self.max_retries + 1):
            try:
                resp = client.get(url, params=params, timeout=10.0)
                resp.raise_for_status()
                return resp.json()
            except (httpx.HTTPError, httpx.TimeoutException) as e:
                log.warning("Attempt %d/%d failed for %s page %d: %s", attempt, self.max_retries, endpoint, page, e)
                if attempt == self.max_retries:
                    raise
                time.sleep(0.5 * attempt)

    def _store_raw(self, endpoint: str, page: int, status: int, payload: dict) -> None:
        self.db.execute(
            """
            INSERT INTO raw.api_responses (endpoint, fetched_at, http_status, payload, page, page_size, record_count)
            VALUES (?, now(), ?, ?::JSON, ?, ?, ?)
            """,
            [endpoint, status, json.dumps(payload), page, self.page_size, len(payload.get("data", []))],
        )

    def extract(self, endpoint: str) -> list[dict]:
        """Fetch all pages from endpoint, store raw blobs, return flat record list."""
        all_records = []
        log.info("[EXTRACT] Starting: %s/%s", self.base_url, endpoint)

        with httpx.Client() as client:
            page = 1
            while True:
                payload = self._fetch_page(client, endpoint, page)
                self._store_raw(endpoint, page, 200, payload)

                batch = payload.get("data", [])
                all_records.extend(batch)
                log.info("  page %d/%d  |  %d records fetched so far", page, payload.get("total_pages", "?"), len(all_records))

                if page >= payload.get("total_pages", 1):
                    break
                page += 1

        log.info("[EXTRACT] Done: %s — %d total records", endpoint, len(all_records))
        return all_records
