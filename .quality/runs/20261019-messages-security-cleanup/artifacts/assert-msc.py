#!/usr/bin/env python3
"""Assertion harness for items #6, #7, #8."""

from __future__ import annotations

import json
import os
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
def m_01() -> None:
    """RoastLevel not found -> 'Roast profile not found' (not 'Farm not found')."""
    probe.psql("delete from users where email='msgprobe@local.test'")
    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Msg", "lastName": "Probe", "username": "msgprobe",
                                                     "phone": "01000000000", "email": "msgprobe@local.test",
                                                     "password": "MsgProbe123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, role='ADMIN', failed_login_attempts=0, locked_until=null "
               "where email='msgprobe@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "msgprobe@local.test", "password": "MsgProbe123!"}),
                            f"{BASE}/api/auth/login")
    tok = (__import__("json").loads(body).get("data") or {}).get("accessToken")

    code, body = call(tok, "GET", "/api/dashboard/roast-levels/does-not-exist")
    check("RoastLevel not found -> 404", code == 404, f"HTTP {code}")
    check("message is 'Roast profile not found' (not 'Farm not found')",
          body.get("message") == "Roast profile not found", repr(body.get("message")))

    code, body = call(tok, "GET", "/api/dashboard/pairings/does-not-exist")
    check("Pairing not found -> 404", code == 404, f"HTTP {code}")
    check("message is 'Pairing not found' (not 'Brewing method not found')",
          body.get("message") == "Pairing not found", repr(body.get("message")))

    probe.psql("delete from login_activities where user_id in (select id from users where email='msgprobe@local.test')")
    probe.psql("delete from refresh_tokens where user_id in (select id from users where email='msgprobe@local.test')")
    probe.psql("delete from users where email='msgprobe@local.test'")


# --------------------------------------------------------------------------- #
def s_01() -> None:
    """CUSTOMER can use addresses but not /api/dashboard/users; ADMIN can."""
    probe.cleanup()
    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Cust", "lastName": "Probe", "username": "custprobe3",
                                                     "phone": "01000000000", "email": "custprobe3@local.test",
                                                     "password": "CustProbe123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, failed_login_attempts=0, locked_until=null where email='custprobe3@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "custprobe3@local.test", "password": "CustProbe123!"}),
                            f"{BASE}/api/auth/login")
    ctok = (__import__("json").loads(body).get("data") or {}).get("accessToken")

    code, _ = call(ctok, "GET", "/api/addresses")
    check("CUSTOMER GET /api/addresses -> 200", code == 200, f"HTTP {code}")
    code, _ = call(ctok, "GET", "/api/dashboard/users")
    check("CUSTOMER GET /api/dashboard/users -> 403 (hole closed)", code == 403, f"HTTP {code}")

    probe.cleanup()
    probe.psql("delete from users where email='adminprobe3@local.test'")
    probe.reset_rate_limits() if hasattr(probe, "reset_rate_limits") else None
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json", "-d",
                            __import__("json").dumps({"firstName": "Admin", "lastName": "Probe", "username": "adminprobe3",
                                                     "phone": "01000000000", "email": "adminprobe3@local.test",
                                                     "password": "AdminProbe123!"}), f"{BASE}/api/auth/register")
    probe.psql("update users set enabled=true, role='ADMIN', failed_login_attempts=0, locked_until=null "
               "where email='adminprobe3@local.test'")
    code, body = probe.curl("-X", "POST", "-H", "Content-Type: application/json",
                            "-d", __import__("json").dumps({"identifier": "adminprobe3@local.test", "password": "AdminProbe123!"}),
                            f"{BASE}/api/auth/login")
    atok = (__import__("json").loads(body).get("data") or {}).get("accessToken")
    code, _ = call(atok, "GET", "/api/dashboard/users")
    check("ADMIN GET /api/dashboard/users -> 200", code == 200, f"HTTP {code}")

    probe.psql("delete from login_activities where user_id in (select id from users where email in ('custprobe3@local.test','adminprobe3@local.test'))")
    probe.psql("delete from refresh_tokens where user_id in (select id from users where email in ('custprobe3@local.test','adminprobe3@local.test'))")
    probe.psql("delete from users where email in ('custprobe3@local.test','adminprobe3@local.test')")


# --------------------------------------------------------------------------- #
def c_01() -> None:
    """Static: duplicate import gone, stale comment gone."""
    farm = os.path.join(ROOT, "src/main/java/com/nocteon/nocteon_api/farm/service/FarmService.java")
    src = open(farm, encoding="utf-8").read()
    check("FarmService no longer imports EntityInUseException twice",
          src.count("import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;") == 1)

    journal = os.path.join(ROOT, "src/main/java/com/nocteon/nocteon_api/journal/service/JournalService.java")
    src = open(journal, encoding="utf-8").read()
    check("JournalService no longer references the non-existent SoftDeleteRepositoryIntegrationTest",
          "SoftDeleteRepositoryIntegrationTest" not in src)


CRITERIA = {"M-01": m_01, "S-01": s_01, "C-01": c_01}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — items #6/#7/#8 ===")
    try:
        CRITERIA[name]()
    except Exception as exc:
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
