#!/usr/bin/env python3
"""Live verification for item #3 (and #4): trash-listing ordering.

Soft-deletes three seeded products (5,6,7), walks the trashed-products listing
under several sort/direction values, prints the resulting order, then restores
the three products. Proves:
  - the default (createdAt desc) is now deterministic and newest-first
  - sort=createdAt asc reverses
  - sort=slug asc sorts by slug
  - journal-category trash listing now honours sort/direction
  - a second identical request returns the same order (no repeat/drop)
"""
from __future__ import annotations

import json
import sys
import time

import probe

BASE = "http://localhost:8080"
TARGETS = ["hario-v60", "chemex-6cup", "aeropress"]   # products 5,6,7


def call(token, method, path, extra=None):
    args = ["-X", method, "-H", f"Authorization: Bearer {token}"]
    if extra:
        args += extra
    return probe.curl(*args, f"{BASE}{path}")


def main() -> int:
    token = probe.provision_token(BASE)
    try:
        # --- soft-delete the three targets --------------------------------
        for slug in TARGETS:
            code, _ = call(token, "DELETE", f"/api/dashboard/products/{slug}")
            print(f"soft-delete {slug}: HTTP {code}")
            if code < 200 or code >= 300:
                raise RuntimeError(f"soft delete failed for {slug}: {code}")

        time.sleep(0.5)

        # --- trashed products under various sorts --------------------------
        def fetch(qs):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?{qs}")
            data = json.loads(body).get("data") or {}
            return code, [r["slug"] for r in (data.get("content") or [])], data

        print("\n=== trashed products ===")
        for qs in ("page=0&size=20",
                   "page=0&size=20&sort=createdAt&direction=desc",
                   "page=0&size=20&sort=createdAt&direction=asc",
                   "page=0&size=20&sort=slug&direction=asc"):
            code, slugs, data = fetch(qs)
            print(f"  {qs:<46} HTTP {code} -> {slugs}")

        # determinism: two identical requests
        _, a, _ = fetch("page=0&size=20&sort=createdAt&direction=desc")
        _, b, _ = fetch("page=0&size=20&sort=createdAt&direction=desc")
        print(f"\n  determinism (two identical requests): {a == b}  ({a})")

        # --- journal-category trash listing (item #4) ----------------------
        print("\n=== trashed journal categories (item #4) ===")
        for qs in ("page=0&size=20",
                   "page=0&size=20&sort=createdAt&direction=desc",
                   "page=0&size=20&sort=createdAt&direction=asc",
                   "page=0&size=20&sort=slug&direction=asc"):
            code, body = call(token, "GET", f"/api/dashboard/journal/categories/deleted?{qs}")
            data = json.loads(body).get("data") or {}
            slugs = [r["slug"] for r in (data.get("content") or [])]
            print(f"  {qs:<46} HTTP {code} -> {slugs}")

        return 0
    finally:
        # --- restore everything -------------------------------------------
        print("\n=== restoring ===")
        for slug in TARGETS:
            # find id via deleted listing is gone after restore; use the known id mapping
            pass
        # restore by id (5,6,7)
        for pid in (5, 6, 7):
            code, _ = call(token, "PATCH", f"/api/dashboard/products/{pid}/restore")
            print(f"  restore id={pid}: HTTP {code}")
        probe.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
