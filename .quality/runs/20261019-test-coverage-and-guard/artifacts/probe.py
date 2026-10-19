#!/usr/bin/env python3
"""Reusable authenticated probe against a running Nocteon API.

Provisions a throwaway ADMIN user (the public API only issues CUSTOMER
registrations), performs one HTTP call, prints status + body, and deletes the
user again — including on failure. The probe's own Bucket4j counters are cleared
first so the script is re-runnable (register allows 3 per 10 min per IP).

Usage:
    probe.py <METHOD> <PATH> [--json <body>] [--form <curl-args...>]
    probe.py <METHOD> <PATH> --token-out <file>      # keep the token for later
    probe.py --base http://localhost:8080 <METHOD> <PATH>
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

PROBE_EMAIL = "probequeue@local.test"
PROBE_USER = "probequeue"
PROBE_PASS = "ProbeQueue123!"
DB_CONTAINER = "nocteon-db"
REDIS_CONTAINER = "nocteon-redis"
DB = "nocteon_db"


def run(cmd: list[str], timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def docker(*args: str, timeout: int = 60) -> subprocess.CompletedProcess:
    return run(["docker", *args], timeout=timeout)


def psql(sql: str) -> str:
    return docker("exec", DB_CONTAINER, "psql", "-U", "postgres", "-d", DB, "-Atc", sql).stdout.strip()


def reset_rate_limits() -> None:
    for pattern in ("rate:register:ip:*", "rate:login:ip:*", f"rate:login:id:{PROBE_EMAIL}"):
        docker("exec", REDIS_CONTAINER, "sh", "-c",
               f"redis-cli --scan --pattern '{pattern}' | xargs -r redis-cli del")


def cleanup() -> None:
    psql("delete from login_activities where user_id in "
         f"(select id from users where email = '{PROBE_EMAIL}')")
    psql("delete from refresh_tokens where user_id in "
         f"(select id from users where email = '{PROBE_EMAIL}')")
    psql(f"delete from users where email = '{PROBE_EMAIL}'")


def curl(*args: str) -> tuple[int, str]:
    p = run(["curl", "-s", "--max-time", "60", "-w", "\n%{http_code}", *args])
    body, _, code = p.stdout.rpartition("\n")
    try:
        return int(code or 0), body
    except ValueError:
        return 0, p.stdout


def provision_token(base: str) -> str:
    cleanup()
    reset_rate_limits()
    code, body = curl("-X", "POST", "-H", "Content-Type: application/json", "-d", json.dumps({
        "firstName": "Probe", "lastName": "Queue", "username": PROBE_USER,
        "phone": "01000000000", "email": PROBE_EMAIL, "password": PROBE_PASS,
    }), f"{base}/api/auth/register")
    if code != 200:
        raise RuntimeError(f"register failed: HTTP {code} {body[:200]}")
    psql(f"update users set enabled = true, role = 'ADMIN', failed_login_attempts = 0, "
         f"locked_until = null where email = '{PROBE_EMAIL}'")
    code, body = curl("-X", "POST", "-H", "Content-Type: application/json",
                      "-d", json.dumps({"identifier": PROBE_EMAIL, "password": PROBE_PASS}),
                      f"{base}/api/auth/login")
    token = ((json.loads(body if body else "{}").get("data") or {}).get("accessToken") or "")
    if code != 200 or not token:
        raise RuntimeError(f"login failed: HTTP {code} {body[:200]}")
    return token


def main() -> int:
    argv = sys.argv[1:]
    base = "http://localhost:8080"
    if argv[:1] == ["--base"]:
        base = argv[1]
        argv = argv[2:]
    method, path = argv[0], argv[1]
    rest = argv[2:]
    out = None
    if "--out" in rest:
        i = rest.index("--out")
        out = rest[i + 1]
        rest = rest[:i] + rest[i + 2:]

    token = provision_token(base)
    try:
        code, body = curl("-X", method, "-H", f"Authorization: Bearer {token}", *rest,
                          f"{base}{path}")
        print(f"{method} {path} -> HTTP {code}")
        print(body[:4000])
        if out:
            with open(out, "w", encoding="utf-8") as fh:
                fh.write(body)
        return 0 if code < 400 else 1
    finally:
        cleanup()


if __name__ == "__main__":
    sys.exit(main())
