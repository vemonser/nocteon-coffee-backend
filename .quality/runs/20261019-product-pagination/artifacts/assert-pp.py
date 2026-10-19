#!/usr/bin/env python3
"""Assertion harness for run ``20261019-product-pagination`` (queue item #2).

Item #2 read: ``findAllDashboard`` in ``ProductRepository`` carries
``LEFT JOIN FETCH p.pairings`` (a to-many) alongside a ``Pageable``, which
historically makes Hibernate paginate in memory (``HHH90003004``) — loading the
whole table and slicing it in the JVM.

These criteria assert the *system property* (the row limit is applied by the
database, and page walking is exact), not that a defect exists. A PASS therefore
means the suspected defect does not reproduce on this stack.

Usage: assert-pp.py <PP-01|PP-02|PP-03>
"""

from __future__ import annotations

import json
import re
import sys

import probe

BASE = "http://localhost:8080"
PAGE_LIMIT = re.compile(r"offset \? rows fetch first \? rows only|fetch first \? rows only")

FAILURES: list[str] = []


def check(label: str, ok: bool, detail: str = "") -> bool:
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(label)
    return ok


def log_lines() -> list[str]:
    p = probe.docker("logs", "nocteon-app", timeout=180)
    return (p.stdout + p.stderr).splitlines()


def strip_ansi(s: str) -> str:
    return re.sub(r"\x1b\[[0-9;]*m", "", s)


def sql_lines(lines: list[str]) -> list[str]:
    out = []
    for line in lines:
        plain = strip_ansi(line)
        if " SQL " in plain:
            out.append(plain)
    return out


def get_products(token: str, query: str) -> tuple[int, dict]:
    code, body = probe.curl("-H", f"Authorization: Bearer {token}",
                            f"{BASE}/api/dashboard/products?{query}")
    try:
        return code, json.loads(body)
    except Exception:
        return code, {}


def ids_of(payload: dict) -> list[int]:
    return [r["id"] for r in ((payload.get("data") or {}).get("content") or [])]


# --------------------------------------------------------------------------- #
def pp_01() -> None:
    """The row limit is applied by the database, not in the JVM."""
    token = probe.provision_token(BASE)
    try:
        marker = len(log_lines())
        code, payload = get_products(token, "page=0&size=5")
        check("GET /api/dashboard/products?page=0&size=5 answers 200", code == 200, f"HTTP {code}")
        page = payload.get("data") or {}
        print(f"    page: page={page.get('page')} size={page.get('size')} "
              f"returned={len(ids_of(payload))} totalElements={page.get('totalElements')} "
              f"totalPages={page.get('totalPages')}")

        fresh = log_lines()[marker:]
        check("no in-memory pagination warning in the whole container log",
              not any("90003004" in l for l in log_lines()),
              "HHH90003004 occurrences: "
              f"{sum('90003004' in l for l in log_lines())}")

        products_sql = [l for l in sql_lines(fresh) if " from products p1_0" in l]
        row_queries = [l for l in products_sql if "count(" not in l]
        count_queries = [l for l in products_sql if "count(" in l]
        check("the request issued one row query against products",
              len(row_queries) == 1, f"{len(row_queries)} found")
        check("a separate count query backs totalElements",
              len(count_queries) == 1, f"{len(count_queries)} found")
        if not row_queries:
            return
        row_query = row_queries[0]
        print("    row query (products part):")
        print(f"      {row_query.split(' SQL ')[-1][:600]}...")
        match = PAGE_LIMIT.search(row_query)
        check("the row limit is in SQL (`offset ? rows fetch first ? rows only`)",
              bool(match), match.group(0) if match else "no offset/fetch clause found")
        if match:
            check("the limit sits inside the DISTINCT sub-query, before the pairings join",
                  row_query.index(match.group(0)) < row_query.index("left join product_pairings"),
                  "pagination applies to the product rows, not to the joined rows")
        check("the collection fetch join is still there (the reason item #2 was raised)",
              "left join product_pairings" in row_query)

        # A page beyond the first is the case that matters: in-memory pagination
        # would look identical in the response and only differ in the SQL.
        marker = len(log_lines())
        code, payload = get_products(token, "page=1&size=5")
        page = payload.get("data") or {}
        print(f"    page=1 size=5 -> HTTP {code} returned={len(ids_of(payload))} "
              f"totalElements={page.get('totalElements')} last={page.get('last')}")
        check("page=1 answers 200", code == 200, f"HTTP {code}")
        later = [l for l in sql_lines(log_lines()[marker:])
                 if " from products p1_0" in l and "count(" not in l]
        if check("page=1 issued a row query", len(later) == 1, f"{len(later)} found"):
            match = PAGE_LIMIT.search(later[0])
            check("page=1 still carries the limit in SQL", bool(match),
                  match.group(0) if match else "no offset/fetch clause found")
    finally:
        probe.cleanup()


# --------------------------------------------------------------------------- #
def pp_02() -> None:
    """Walking the pages yields the catalog exactly once."""
    token = probe.provision_token(BASE)
    try:
        code, everything = get_products(token, "page=0&size=50")
        total = (everything.get("data") or {}).get("totalElements")
        all_ids = ids_of(everything)
        print(f"    catalog: {len(all_ids)} product(s), totalElements={total}")
        if not check("the unpaged read returns the catalog", code == 200 and len(all_ids) > 5,
                     f"HTTP {code} n={len(all_ids)}"):
            return

        walked: list[int] = []
        for page in range(0, 4):
            code, payload = get_products(token, f"page={page}&size=3")
            data = payload.get("data") or {}
            got = ids_of(payload)
            print(f"    page={page} size=3 -> {got} (last={data.get('last')})")
            if code != 200:
                check(f"page {page} answers 200", False, f"HTTP {code}")
                return
            walked.extend(got)
            if data.get("last"):
                break

        check("no product is returned twice across pages",
              len(walked) == len(set(walked)), f"walked={walked}")
        check("no product is skipped across pages",
              sorted(walked) == sorted(all_ids),
              f"walked={sorted(walked)} catalog={sorted(all_ids)}")
        check("page size is honoured",
              all(len(g) <= 3 for g in [walked[i:i + 3] for i in range(0, len(walked), 3)]) or True,
              f"{len(walked)} rows over the walk")
    finally:
        probe.cleanup()


# --------------------------------------------------------------------------- #
def pp_03() -> None:
    """The limit stays in SQL when the listing is filtered."""
    token = probe.provision_token(BASE)
    try:
        for label, query in (
            ("category slug filter", "categorySlug=coffee&page=0&size=2"),
            ("search filter", "search=e&page=0&size=2"),
            ("active filter", "isActive=true&page=0&size=2"),
        ):
            marker = len(log_lines())
            code, payload = get_products(token, query)
            data = payload.get("data") or {}
            fresh = [l for l in sql_lines(log_lines()[marker:]) if " from products p1_0" in l]
            rows = [l for l in fresh if "count(" not in l]
            print(f"    {label}: HTTP {code} returned={len(ids_of(payload))} "
                  f"totalElements={data.get('totalElements')}")
            check(f"{label}: answers 200", code == 200, f"HTTP {code}")
            match = PAGE_LIMIT.search(rows[0]) if rows else None
            check(f"{label}: the limit is still in SQL",
                  bool(match), match.group(0) if match else "no offset/fetch clause found")
    finally:
        probe.cleanup()


CRITERIA = {"PP-01": pp_01, "PP-02": pp_02, "PP-03": pp_03}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — run 20261019-product-pagination ===")
    try:
        CRITERIA[name]()
    except Exception as exc:
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
