#!/usr/bin/env python3
"""Assertion harness for item #9: live-verify the remaining trash flows."""

from __future__ import annotations

import json
import os
import re
import sys

import probe

BASE = "http://localhost:8080"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))

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
def n_01() -> None:
    """Static: all 13 trash controllers expose /deleted, /restore, /permanent."""
    controllers = [
        "brewingMethod", "category", "coffeeVariety", "farm", "journal", "origin",
        "pairing", "processingMethod", "product", "promoCode", "roastLevel",
        "shippingZone", "tastingNote",
    ]
    # journal exposes journal-categories too; check the journal controller covers both
    paths_needed = {"/deleted", "/restore", "/permanent"}
    missing = []
    for name in controllers:
        # find the controller file
        matches = [p for p in __import__("pathlib").Path(ROOT, "src/main/java").rglob(f"*{name.capitalize()}Controller.java".replace("Brewingmethod","BrewingMethod"))]
        # fallback search
        if not matches:
            matches = [p for p in __import__("pathlib").Path(ROOT, "src/main/java").rglob("*.java") if p.name.lower() == f"{name.lower()}controller.java".replace("journal","journal")]
        if not matches:
            missing.append(f"{name}: controller file not found")
            continue
        src = matches[0].read_text(encoding="utf-8")
        for p in paths_needed:
            if p not in src:
                missing.append(f"{name}: missing {p}")
    check("all 13 controllers expose /deleted, /restore, /permanent",
          not missing, "; ".join(missing) if missing else "13/13")
    # journal must also expose journal-categories endpoints
    jc = __import__("pathlib").Path(ROOT, "src/main/java/com/nocteon/nocteon_api/journal/controller/JournalController.java").read_text(encoding="utf-8")
    check("JournalController also exposes journal-categories trash endpoints",
          all(p in jc for p in ("/dashboard/journal-categories/deleted",
                                "/dashboard/journal-categories/{id}/restore",
                                "/dashboard/journal-categories/{id}/permanent")))


# --------------------------------------------------------------------------- #
def n_02() -> None:
    """Live: journal post trash flow exercises the JournalPostStatus enum cast."""
    probe.psql("delete from journal_post_translations where journal_post_id=9999")
    probe.psql("delete from journal_posts where id=9999")
    probe.psql("insert into journal_posts (id, journal_category_id, author_id, slug, status, created_at, updated_at, deleted_at) "
               "values (9999, 1, 1, 'zz-trash-enum-test', 'PUBLISHED', now(), now(), now())")
    probe.psql("insert into journal_post_translations (journal_post_id, language, title, content) "
               "values (9999, 'en', 'ZZ Enum Test', 'content')")

    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    probe.psql("delete from users where email='jp9@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Jp", "lastName": "Probe", "username": "jp9",
                                                     "phone": "01000000000", "email": "jp9@local.test",
                                                     "password": "JpProbe123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, role='ADMIN', failed_login_attempts=0, locked_until=null "
               "where email='jp9@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "jp9@local.test", "password": "JpProbe123!"}),
                            f"{BASE}/api/auth/login")
    tok = (__import__("json").loads(body).get("data") or {}).get("accessToken")

    code, body = call(tok, "GET", "/api/dashboard/journal/deleted?page=0&size=50")
    d = json.loads(body) if isinstance(body, str) else body
    # body is already parsed in call(); re-parse from raw not available; use d
    rows = (d.get("data") or {}).get("content") or []
    found = [r for r in rows if r.get("slug") == "zz-trash-enum-test"]
    check("journal trash listing answers 200", code == 200, f"HTTP {code}")
    check("the seeded PUBLISHED post appears (enum cast succeeded)",
          bool(found) and found[0].get("status") == "PUBLISHED",
          str(found[0].get("status")) if found else "not found")

    # cleanup
    probe.psql("delete from journal_post_translations where journal_post_id=9999")
    probe.psql("delete from journal_posts where id=9999")
    probe.psql("delete from login_activities where user_id in (select id from users where email='jp9@local.test')")
    probe.psql("delete from refresh_tokens where user_id in (select id from users where email='jp9@local.test')")
    probe.psql("delete from users where email='jp9@local.test'")


# --------------------------------------------------------------------------- #
def n_03() -> None:
    """Live: promo code trash flow (entity pattern, not folded rows)."""
    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    probe.psql("delete from users where email='admin9@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Admin", "lastName": "Nine", "username": "admin9",
                                                     "phone": "01000000000", "email": "admin9@local.test",
                                                     "password": "AdminNine123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, role='ADMIN', failed_login_attempts=0, locked_until=null "
               "where email='admin9@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "admin9@local.test", "password": "AdminNine123!"}),
                            f"{BASE}/api/auth/login")
    tok = (__import__("json").loads(body).get("data") or {}).get("accessToken")

    # soft-delete promo code 2 (SAVE20), verify it appears, restore
    code, _ = probe.curl("-X", "DELETE", "-H", f"Authorization: Bearer {tok}",
                         f"{BASE}/api/dashboard/promo-codes/2")
    check("promo code soft-delete -> 2xx", 200 <= code < 300, f"HTTP {code}")

    code, body = call(tok, "GET", "/api/dashboard/promo-codes/deleted?page=0&size=20")
    d = body.get("data") or {}
    codes = [r.get("code") for r in (d.get("content") or [])]
    check("promo code appears in trash", "SAVE20" in codes, str(codes))

    code, _ = probe.curl("-X", "PATCH", "-H", f"Authorization: Bearer {tok}",
                         f"{BASE}/api/dashboard/promo-codes/2/restore")
    check("promo code restore -> 2xx", 200 <= code < 300, f"HTTP {code}")

    code, body = call(tok, "GET", "/api/dashboard/promo-codes/deleted?page=0&size=20")
    d = body.get("data") or {}
    codes = [r.get("code") for r in (d.get("content") or [])]
    check("promo code gone from trash after restore", "SAVE20" not in codes, str(codes))

    probe.psql("delete from login_activities where user_id in (select id from users where email='admin9@local.test')")
    probe.psql("delete from refresh_tokens where user_id in (select id from users where email='admin9@local.test')")
    probe.psql("delete from users where email='admin9@local.test'")


# --------------------------------------------------------------------------- #
def n_04() -> None:
    """Live: shipping zone trash flow (entity pattern, different DTO)."""
    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    probe.psql("delete from users where email='admin9b@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Admin", "lastName": "Nineb", "username": "admin9b",
                                                     "phone": "01000000000", "email": "admin9b@local.test",
                                                     "password": "AdminNineb123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, role='ADMIN', failed_login_attempts=0, locked_until=null "
               "where email='admin9b@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "admin9b@local.test", "password": "AdminNineb123!"}),
                            f"{BASE}/api/auth/login")
    tok = (__import__("json").loads(body).get("data") or {}).get("accessToken")

    code, _ = probe.curl("-X", "DELETE", "-H", f"Authorization: Bearer {tok}",
                         f"{BASE}/api/dashboard/shipping-zones/1")
    check("shipping zone soft-delete -> 2xx", 200 <= code < 300, f"HTTP {code}")

    code, body = call(tok, "GET", "/api/dashboard/shipping-zones/deleted?page=0&size=20")
    d = body.get("data") or {}
    names = [r.get("name") for r in (d.get("content") or [])]
    check("shipping zone appears in trash", "Cairo Zone" in names, str(names))

    code, _ = probe.curl("-X", "PATCH", "-H", f"Authorization: Bearer {tok}",
                         f"{BASE}/api/dashboard/shipping-zones/1/restore")
    check("shipping zone restore -> 2xx", 200 <= code < 300, f"HTTP {code}")

    code, body = call(tok, "GET", "/api/dashboard/shipping-zones/deleted?page=0&size=20")
    d = body.get("data") or {}
    names = [r.get("name") for r in (d.get("content") or [])]
    check("shipping zone gone from trash after restore", "Cairo Zone" not in names, str(names))

    probe.psql("delete from login_activities where user_id in (select id from users where email='admin9b@local.test')")
    probe.psql("delete from refresh_tokens where user_id in (select id from users where email='admin9b@local.test')")
    probe.psql("delete from users where email='admin9b@local.test'")


# --------------------------------------------------------------------------- #
def n_05() -> None:
    """Static: the 8 remaining entities follow the same safe pattern."""
    services = ["BrewingMethod", "CoffeeVariety", "Farm", "Origin", "Pairing",
                "ProcessingMethod", "RoastLevel", "TastingNote"]
    bad = []
    for name in services:
        # find the service file
        matches = [p for p in __import__("pathlib").Path(ROOT, "src/main/java").rglob(f"{name}Service.java")]
        if not matches:
            bad.append(f"{name}: service file not found")
            continue
        src = matches[0].read_text(encoding="utf-8")
        # must have a getAllDeleted method (trash flow exists)
        if "getAllDeleted" not in src:
            bad.append(f"{name}: no getAllDeleted")
            continue
        # must use SoftDeleteRowFolder.fold (same pattern)
        if "SoftDeleteRowFolder.fold" not in src:
            bad.append(f"{name}: not using SoftDeleteRowFolder.fold")
            continue
        # no enum valueOf casts in the trash mapper (the risky kind)
        if "valueOf((String) row" in src or "valueOf((String)row" in src:
            bad.append(f"{name}: risky enum valueOf cast in trash mapper")
            continue
        # casts must be to safe types only
        casts = set(__import__("re").findall(r"\(([A-Za-z]+)\)\s*row\[", src))
        allowed = {"Number", "String", "Instant", "Boolean"}
        risky = casts - allowed
        if risky:
            bad.append(f"{name}: risky casts {risky}")
    check("the 8 remaining entities follow the safe trash pattern",
          not bad, "; ".join(bad) if bad else "8/8 (no enum casts, only Number/String/Instant/Boolean)")


CRITERIA = {"N-01": n_01, "N-02": n_02, "N-03": n_03, "N-04": n_04, "N-05": n_05}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — item #9 (live verification of remaining trash flows) ===")
    try:
        CRITERIA[name]()
    except Exception as exc:
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
