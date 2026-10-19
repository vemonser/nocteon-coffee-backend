#!/usr/bin/env python3
"""Live verification for items #3 and #4 with distinct timestamps.

Sets distinct created_at on three products and one journal category via SQL,
soft-deletes them, exercises createdAt asc/desc + slug asc + determinism,
then restores everything and resets created_at to its original value.
"""
from __future__ import annotations

import json
import sys
import time

import probe

BASE = "http://localhost:8080"
PRODUCT_IDS = (5, 6, 7)
PRODUCT_SLUGS = {5: "hario-v60", 6: "chemex-6cup", 7: "aeropress"}
JC_ID = 1
JC_SLUG = "brewing-guides"


def call(token, method, path):
    code, body = probe.curl("-X", method, "-H", f"Authorization: Bearer {token}", f"{BASE}{path}")
    try:
        return code, json.loads(body)
    except Exception:
        return code, {}


def main() -> int:
    # snapshot original timestamps
    orig_prod = {pid: probe.psql(f"select created_at from products where id={pid}") for pid in PRODUCT_IDS}
    orig_jc = probe.psql(f"select created_at from journal_categories where id={JC_ID}")
    print("original product timestamps:", orig_prod)
    print("original journal-category timestamp:", orig_jc)

    token = probe.provision_token(BASE)
    try:
        # set distinct created_at (5 oldest, 7 newest)
        probe.psql("update products set created_at = '2026-01-01 00:00:00+00' where id=5")
        probe.psql("update products set created_at = '2026-02-02 00:00:00+00' where id=6")
        probe.psql("update products set created_at = '2026-03-03 00:00:00+00' where id=7")
        probe.psql(f"update journal_categories set created_at = '2026-01-01 00:00:00+00' where id={JC_ID}")

        # soft-delete
        for pid in PRODUCT_IDS:
            code, _ = call(token, "DELETE", f"/api/dashboard/products/{PRODUCT_SLUGS[pid]}")
            print(f"soft-delete product {pid}: HTTP {code}")
        probe.psql(f"update journal_categories set deleted_at = now() where id = {JC_ID}")
        print(f"soft-delete journal category {JC_ID}: via SQL")

        time.sleep(0.5)

        print("\n=== trashed products ===")
        def prod(qs):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?{qs}")
            d = body.get("data") or {}
            return code, [r["slug"] for r in (d.get("content") or [])]

        for qs in ("page=0&size=20",
                   "page=0&size=20&sort=createdAt&direction=desc",
                   "page=0&size=20&sort=createdAt&direction=asc",
                   "page=0&size=20&sort=slug&direction=asc"):
            code, slugs = prod(qs)
            print(f"  {qs:<46} HTTP {code} -> {slugs}")

        _, a = prod("page=0&size=20&sort=createdAt&direction=desc")
        _, b = prod("page=0&size=20&sort=createdAt&direction=desc")
        print(f"  determinism: {a == b}")

        # page walk
        walked = []
        for pg in range(0, 3):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?page={pg}&size=2")
            d = body.get("data") or {}
            walked.extend(r["slug"] for r in (d.get("content") or []))
            if d.get("last"):
                break
        print(f"  page walk size=2: {walked}")

        print("\n=== trashed journal categories (item #4) ===")
        def jc(qs):
            code, body = call(token, "GET", f"/api/dashboard/journal/categories/deleted?{qs}")
            d = body.get("data") or {}
            return code, [r["slug"] for r in (d.get("content") or [])]

        for qs in ("page=0&size=20",
                   "page=0&size=20&sort=createdAt&direction=desc",
                   "page=0&size=20&sort=createdAt&direction=asc",
                   "page=0&size=20&sort=slug&direction=asc"):
            code, slugs = jc(qs)
            print(f"  {qs:<46} HTTP {code} -> {slugs}")

        return 0
    finally:
        print("\n=== restoring ===")
        for pid in PRODUCT_IDS:
            code, _ = call(token, "PATCH", f"/api/dashboard/products/{pid}/restore")
            print(f"  restore product {pid}: HTTP {code}")
        code, _ = call(token, "PATCH", f"/api/dashboard/journal/categories/{JC_ID}/restore")
        print(f"  restore journal category {JC_ID}: HTTP {code}")
        # reset timestamps
        for pid, ts in orig_prod.items():
            probe.psql(f"update products set created_at = '{ts}' where id={pid}")
        probe.psql(f"update journal_categories set created_at = '{orig_jc}' where id={JC_ID}")
        print("  timestamps reset")
        probe.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
