#!/usr/bin/env python3
"""Assertion harness for the trash-listing ordering fix (items #3 and #4).

TR-01  static: every trash-bin native query carries ORDER BY created_at.
TR-02  live:   Product trash listing honours createdAt asc/desc.
TR-03  live:   Product trash listing is deterministic across repeated requests.
TR-04  live:   Journal-category trash listing honours sort/direction (was ignored).
"""
from __future__ import annotations

import json
import os
import re
import sys

import probe

BASE = "http://localhost:8080"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
REPOS = {
    "Product": "src/main/java/com/nocteon/nocteon_api/product/repository/ProductRepository.java",
    "Category": "src/main/java/com/nocteon/nocteon_api/category/repository/CategoryRepository.java",
    "JournalPost": "src/main/java/com/nocteon/nocteon_api/journal/repository/JournalPostRepository.java",
    "JournalCategory": "src/main/java/com/nocteon/nocteon_api/journal/repository/JournalCategoryRepository.java",
}

FAILURES: list[str] = []


def check(label: str, ok: bool, detail: str = "") -> bool:
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(label)
    return ok


def call(token, method, path):
    code, body = probe.curl("-X", method, "-H", f"Authorization: Bearer {token}", f"{BASE}{path}")
    try:
        return code, json.loads(body)
    except Exception:
        return code, {}


# --------------------------------------------------------------------------- #
def tr_01() -> None:
    print("  native trash queries and their ORDER BY clause:")
    for name, rel in REPOS.items():
        src = open(os.path.join(ROOT, rel), encoding="utf-8").read()
        # every native trash query is a @Query(... nativeQuery = true) containing
        # "deleted_at IS NOT NULL"; count them and check each has ORDER BY created_at
        blocks = re.findall(r'@Query\(value\s*=\s*"""(.*?)"""\s*,\s*nativeQuery\s*=\s*true\)',
                            src, re.S)
        trash = [b for b in blocks if "deleted_at IS NOT NULL" in b]
        ordered = [b for b in trash if re.search(r"ORDER BY\s+\w+\.created_at", b, re.I)]
        print(f"    {name:<16} trash-queries={len(trash)} ordered={len(ordered)}")
        check(f"{name}: every trash query has ORDER BY ... created_at",
              len(trash) > 0 and len(ordered) == len(trash),
              f"{len(ordered)}/{len(trash)}")


# --------------------------------------------------------------------------- #
def tr_02() -> None:
    # distinct timestamps
    probe.psql("update products set created_at = '2026-01-01 00:00:00+00' where id=5")
    probe.psql("update products set created_at = '2026-02-02 00:00:00+00' where id=6")
    probe.psql("update products set created_at = '2026-03-03 00:00:00+00' where id=7")
    probe.psql("update products set deleted_at = now() where id in (5,6,7)")

    token = probe.provision_token(BASE)
    try:
        def fetch(qs):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?{qs}")
            d = body.get("data") or {}
            return code, [r["slug"] for r in (d.get("content") or [])]

        code, desc = fetch("page=0&size=20&sort=createdAt&direction=desc")
        code2, asc = fetch("page=0&size=20&sort=createdAt&direction=asc")
        print(f"    desc -> {desc}")
        print(f"    asc  -> {asc}")
        check("Product: createdAt desc yields newest-first",
              desc == ["aeropress", "chemex-6cup", "hario-v60"], str(desc))
        check("Product: createdAt asc reverses the order",
              asc == ["hario-v60", "chemex-6cup", "aeropress"], str(asc))
        check("Product: slug asc sorts alphabetically",
              fetch("page=0&size=20&sort=slug&direction=asc")[1]
              == ["aeropress", "chemex-6cup", "hario-v60"])
    finally:
        for pid in (5, 6, 7):
            call(token, "PATCH", f"/api/dashboard/products/{pid}/restore")
        for pid, ts in ((5, "2026-10-19 05:15:35.713312+00"),
                        (6, "2026-10-19 05:15:35.713312+00"),
                        (7, "2026-10-19 05:15:35.713312+00")):
            probe.psql(f"update products set created_at = '{ts}' where id={pid}")
        probe.cleanup()


# --------------------------------------------------------------------------- #
def tr_03() -> None:
    # distinct timestamps again
    probe.psql("update products set created_at = '2026-01-01 00:00:00+00' where id=5")
    probe.psql("update products set created_at = '2026-02-02 00:00:00+00' where id=6")
    probe.psql("update products set created_at = '2026-03-03 00:00:00+00' where id=7")
    probe.psql("update products set deleted_at = now() where id in (5,6,7)")

    token = probe.provision_token(BASE)
    try:
        def fetch(qs):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?{qs}")
            d = body.get("data") or {}
            return code, [r["slug"] for r in (d.get("content") or [])]

        a = fetch("page=0&size=20&sort=createdAt&direction=desc")[1]
        b = fetch("page=0&size=20&sort=createdAt&direction=desc")[1]
        print(f"    run 1 -> {a}")
        print(f"    run 2 -> {b}")
        check("Product: identical requests return byte-identical order", a == b, str(a))
        # page walk
        walked = []
        for pg in range(0, 3):
            code, body = call(token, "GET", f"/api/dashboard/products/deleted?page={pg}&size=2&sort=createdAt&direction=desc")
            d = body.get("data") or {}
            walked.extend(r["slug"] for r in (d.get("content") or []))
            if d.get("last"):
                break
        check("Product: page walk has no duplicate or gap",
              len(walked) == len(set(walked)) == 3, str(walked))
    finally:
        for pid in (5, 6, 7):
            call(token, "PATCH", f"/api/dashboard/products/{pid}/restore")
        for pid in (5, 6, 7):
            probe.psql(f"update products set created_at = '2026-10-19 05:15:35.713312+00' where id={pid}")
        probe.cleanup()


# --------------------------------------------------------------------------- #
def tr_04() -> None:
    # seed two trashed journal categories with distinct created_at + slugs
    probe.psql("update journal_categories set deleted_at = now(), created_at = '2026-01-01 00:00:00+00' where id=1")
    probe.psql("""insert into journal_categories (id, slug, created_at, updated_at, deleted_at)
                  values (999, 'zz-trashed-cat', '2026-03-03 00:00:00+00', now(), now())
                  on conflict (id) do nothing""")
    probe.psql("""insert into journal_category_translations (journal_category_id, language, name)
                  values (999, 'en', 'ZZ Trashed') on conflict do nothing""")

    token = probe.provision_token(BASE)
    try:
        def fetch(qs):
            code, body = call(token, "GET", f"/api/dashboard/journal-categories/deleted?{qs}")
            d = body.get("data") or {}
            return code, [r["slug"] for r in (d.get("content") or [])]

        code, desc = fetch("page=0&size=20&sort=createdAt&direction=desc")
        code2, asc = fetch("page=0&size=20&sort=createdAt&direction=asc")
        code3, slugs = fetch("page=0&size=20&sort=slug&direction=asc")
        print(f"    desc -> {desc}")
        print(f"    asc  -> {asc}")
        print(f"    slug -> {slugs}")
        check("JournalCategory: createdAt desc yields newest-first",
              desc and desc[0] == "zz-trashed-cat", str(desc))
        check("JournalCategory: createdAt asc reverses",
              asc and asc[-1] == "zz-trashed-cat", str(asc))
        check("JournalCategory: slug asc sorts alphabetically",
              slugs == sorted(slugs), str(slugs))
        check("JournalCategory: determinism",
              fetch("page=0&size=20&sort=createdAt&direction=desc")[1] == desc)
    finally:
        # restore + cleanup
        call(token, "PATCH", "/api/dashboard/journal-categories/1/restore")
        probe.psql("delete from journal_category_translations where journal_category_id=999")
        probe.psql("delete from journal_categories where id=999")
        probe.psql("update journal_categories set created_at = '2026-10-19 05:15:35.657743+00', deleted_at = null where id=1")
        probe.cleanup()


CRITERIA = {"TR-01": tr_01, "TR-02": tr_02, "TR-03": tr_03, "TR-04": tr_04}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — trash-listing ordering (items #3/#4) ===")
    try:
        CRITERIA[name]()
    except Exception as exc:
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
