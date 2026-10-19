#!/usr/bin/env python3
"""Assertion harness for run ``20261019-container-healthcheck``.

One subcommand per acceptance criterion of
``.quality/runs/20261019-container-healthcheck/CHANGE-CONTRACT.md``.
Exit 0 means the criterion holds; 1 means it does not.

``HC-05`` provisions a throwaway ADMIN probe user (the public API only issues
CUSTOMER registrations) and removes it again in a ``finally`` block, so the
criterion is re-runnable and leaves no rows behind.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
DOCKERFILE = os.path.join(ROOT, "Dockerfile")
BASE = "http://localhost:8080"
CONTAINER = "nocteon-app"
DB_CONTAINER = "nocteon-db"
REDIS_CONTAINER = "nocteon-redis"
DB = "nocteon_db"
PROBE_EMAIL = "trashprobe8080@local.test"
PROBE_USER = "trashprobe8080"
PROBE_PASS = "TrashProbe123!"
EXPECTED_TRASH_CONTROLLERS = 13

FAILURES: list[str] = []


def check(label: str, ok: bool, detail: str = "") -> bool:
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(label)
    return ok


def run(cmd: list[str], timeout: int = 120) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def docker(*args: str, timeout: int = 120) -> subprocess.CompletedProcess:
    return run(["docker", *args], timeout=timeout)


def psql(sql: str) -> str:
    return docker(
        "exec", DB_CONTAINER, "psql", "-U", "postgres", "-d", DB, "-Atc", sql
    ).stdout.strip()


def curl(*args: str) -> tuple[int, str]:
    """Return (http_status, body). Uses curl for its multipart support."""
    p = run(["curl", "-s", "--max-time", "30", "-w", "\n%{http_code}", *args])
    body, _, code = p.stdout.rpartition("\n")
    try:
        return int(code or 0), body
    except ValueError:
        return 0, p.stdout


def body_json(body: str) -> dict:
    try:
        return json.loads(body)
    except Exception:
        return {}


def reset_probe_rate_limits() -> None:
    """Harness provisioning: clear the probe's own rate-limit counters.

    ``POST /api/auth/register`` allows only 3 requests per 10 minutes per IP
    (``RateLimiterService.registerBucketConfig``) and login allows 5 per minute,
    so a re-runnable criterion would otherwise be gated by a bucket the previous
    run of itself drained. These are ephemeral Bucket4j counters in Redis — no
    application data — and only the probe-facing keys are touched.
    """
    for pattern in ("rate:register:ip:*", "rate:login:ip:*", f"rate:login:id:{PROBE_EMAIL}"):
        p = docker("exec", REDIS_CONTAINER, "sh", "-c",
                   f"redis-cli --scan --pattern '{pattern}' | xargs -r redis-cli del")
        print(f"    reset {pattern}: {(p.stdout or '').strip() or 'no keys'}")


# --------------------------------------------------------------------------- #
# HC-01 — the healthcheck probes with GET, not HEAD
# --------------------------------------------------------------------------- #
def hc_01() -> None:
    print("effectively-probed command, from the Dockerfile:")
    lines = open(DOCKERFILE, encoding="utf-8").read().splitlines()
    idx = [i for i, l in enumerate(lines) if l.startswith("HEALTHCHECK")]
    if not check("Dockerfile declares a HEALTHCHECK", bool(idx)):
        return
    i = idx[0]
    cmd = " ".join(lines[i : i + 2]).strip()
    print(f"    {cmd}")
    check("the probe no longer uses `--spider` (HEAD)", "--spider" not in cmd)
    check("the probe forces a real GET via `-O /dev/null`", "-O /dev/null" in cmd)
    check("the probed URL is unchanged", "/api/settings" in cmd)
    check("interval/timeout/start-period/retries untouched",
          all(t in cmd for t in ("interval=30s", "timeout=10s", "start-period=60s", "retries=3")))


# --------------------------------------------------------------------------- #
# HC-02 — /api/settings answers 200 to a GET from inside the container
# --------------------------------------------------------------------------- #
def hc_02() -> None:
    p = docker("exec", CONTAINER, "wget", "--no-verbose", "--tries=1", "-O", "/dev/null",
               f"{BASE}/api/settings")
    print(f"    new probe  -> exit {p.returncode}: {(p.stdout + p.stderr).strip()[:160]}")
    check("the new probe exits 0 inside the container", p.returncode == 0,
          f"exit={p.returncode}")

    old = docker("exec", CONTAINER, "wget", "--no-verbose", "--tries=1", "--spider",
                 f"{BASE}/api/settings")
    print(f"    old probe  -> exit {old.returncode}: {(old.stdout + old.stderr).strip()[:160]}"
          "   (informational: documents why the container used to be unhealthy)")

    code, _ = curl("-o", "/dev/null", f"{BASE}/api/settings")
    check("GET /api/settings answers 200 from the host too", code == 200, f"HTTP {code}")

    # A probe that always exits 0 would be worse than the bug it replaced.
    broken = docker("exec", CONTAINER, "wget", "--no-verbose", "--tries=1", "-O", "/dev/null",
                    f"{BASE}/api/not-a-real-endpoint")
    print(f"    new probe vs a non-2xx URL -> exit {broken.returncode}: "
          f"{(broken.stdout + broken.stderr).strip()[:120]}")
    check("the probe still fails on a non-2xx response (not vacuous)", broken.returncode != 0,
          f"exit={broken.returncode}")


# --------------------------------------------------------------------------- #
# HC-03 — the container reaches and keeps `healthy`
# --------------------------------------------------------------------------- #
def hc_03() -> None:
    raw = docker("inspect", CONTAINER, "--format", "{{json .State.Health}}").stdout.strip()
    health = json.loads(raw)
    print(f"    status={health['Status']} failingStreak={health['FailingStreak']}")
    for e in (health.get("Log") or [])[-3:]:
        print(f"      {e['Start']} exit={e['ExitCode']} {(e.get('Output') or '').strip()[:100]}")
    check("health status is `healthy`", health["Status"] == "healthy", health["Status"])
    check("no consecutive probe failures", health["FailingStreak"] == 0,
          f"streak={health['FailingStreak']}")
    recent = (health.get("Log") or [])[-3:]
    check("the last probes all recorded exit 0", bool(recent) and all(e["ExitCode"] == 0 for e in recent))

    restarts = docker("inspect", CONTAINER, "--format", "{{.RestartCount}}").stdout.strip()
    check("the container has not had to restart", restarts == "0", f"restarts={restarts}")


# --------------------------------------------------------------------------- #
# HC-04 — :8080 serves the recovered build
# --------------------------------------------------------------------------- #
def hc_04() -> None:
    jar = "/tmp/opencode/recovery/app-under-test.jar"
    os.makedirs(os.path.dirname(jar), exist_ok=True)
    p = docker("cp", f"{CONTAINER}:/app/app.jar", jar)
    if not check("the running jar can be extracted from the container", p.returncode == 0,
                 p.stderr.strip()[:160]):
        return

    stat = docker("exec", CONTAINER, "stat", "-c", "%y %s", "/app/app.jar").stdout.strip()
    print(f"    /app/app.jar: {stat}")

    z = zipfile.ZipFile(jar)
    cat = [n for n in z.namelist() if n.endswith("CategoryController.class")]
    if not check("CategoryController is in the jar", bool(cat)):
        return
    data = z.read(cat[0])
    for needle in (b"/dashboard/categories", b"/deleted", b"restore", b"permanent"):
        check(f"CategoryController carries {needle.decode()!r}", needle in data)

    trashy = sorted(n.rsplit("/", 1)[1][: -len(".class")]
                    for n in z.namelist()
                    if n.endswith("Controller.class") and b"/deleted" in z.read(n))
    print(f"    controllers exposing /deleted ({len(trashy)}): {', '.join(trashy)}")
    check("every catalog controller from the recovery shipped",
          len(trashy) == EXPECTED_TRASH_CONTROLLERS,
          f"{len(trashy)} of {EXPECTED_TRASH_CONTROLLERS}")

    check("the recovered SoftDeleteRowFolder helper shipped",
          any(n.endswith("SoftDeleteRowFolder.class") for n in z.namelist()))


# --------------------------------------------------------------------------- #
# HC-05 — the recovered endpoints answer on :8080
# --------------------------------------------------------------------------- #
def hc_05() -> None:
    code, _ = curl("-o", "/dev/null", f"{BASE}/api/dashboard/categories/deleted")
    check("without a token the route is still closed", code == 401, f"HTTP {code}")

    psql(f"delete from users where email = '{PROBE_EMAIL}'")
    reset_probe_rate_limits()
    code, body = curl("-X", "POST", "-H", "Content-Type: application/json", "-d", json.dumps({
        "firstName": "Trash", "lastName": "Probe", "username": PROBE_USER,
        "phone": "01000000000", "email": PROBE_EMAIL, "password": PROBE_PASS,
    }), f"{BASE}/api/auth/register")
    if not check("the probe user registers", code == 200, f"HTTP {code}"):
        return
    psql(f"update users set enabled = true, role = 'ADMIN', failed_login_attempts = 0, "
         f"locked_until = null where email = '{PROBE_EMAIL}'")

    code, body = curl("-X", "POST", "-H", "Content-Type: application/json",
                      "-d", json.dumps({"identifier": PROBE_EMAIL, "password": PROBE_PASS}),
                      f"{BASE}/api/auth/login")
    token = ((body_json(body).get("data") or {}).get("accessToken") or "")
    if not check("the probe user logs in", code == 200 and bool(token), f"HTTP {code}"):
        return
    auth = ["-H", f"Authorization: Bearer {token}"]

    try:
        code, body = curl(*auth, f"{BASE}/api/dashboard/categories/deleted")
        d = body_json(body)
        check("GET /api/dashboard/categories/deleted answers 200 (the user's original URL)",
              code == 200, f"HTTP {code}")
        check("its message is the trash-bin listing, not `Category not found`",
              d.get("message") == "Deleted categories retrieved", repr(d.get("message")))

        code, body = curl(*auth, f"{BASE}/api/dashboard/categories/definitely-not-a-slug")
        d = body_json(body)
        check("an unknown slug of the same shape still falls through to GET /{slug}",
              code == 404 and d.get("message") == "Category not found",
              f"HTTP {code} {d.get('message')!r}")

        name = f"ZZ HC05 Probe {int(time.time())}"
        payload = json.dumps({"isActive": True, "translations": [
            {"language": "en", "name": name, "description": "probe"},
            {"language": "ar", "name": name + " ar", "description": "probe"}]})
        code, body = curl(*auth, "-X", "POST", "-F", f"data={payload};type=application/json",
                          f"{BASE}/api/dashboard/categories")
        created = body_json(body).get("data") or {}
        cid, slug = created.get("id"), created.get("slug")
        if not check("a throwaway category is created", code == 201 and bool(cid),
                     f"HTTP {code} id={cid} slug={slug}"):
            return

        code, _ = curl("-o", "/dev/null", *auth, "-X", "DELETE",
                       f"{BASE}/api/dashboard/categories/{slug}")
        check("soft delete answers 204", code == 204, f"HTTP {code}")

        code, body = curl(*auth, f"{BASE}/api/dashboard/categories/deleted")
        check("the row is listed in the trash bin",
              cid in [r["id"] for r in ((body_json(body).get("data") or {}).get("content") or [])])

        code, body = curl(*auth, "-X", "PATCH", f"{BASE}/api/dashboard/categories/{cid}/restore")
        check("restore answers 200 with `Category restored`",
              code == 200 and body_json(body).get("message") == "Category restored",
              f"HTTP {code} {body_json(body).get('message')!r}")

        code, body = curl(*auth, f"{BASE}/api/dashboard/categories/deleted")
        check("the restored row left the trash bin",
              cid not in [r["id"] for r in ((body_json(body).get("data") or {}).get("content") or [])])

        code, _ = curl("-o", "/dev/null", *auth, f"{BASE}/api/dashboard/categories/{slug}")
        check("the restored row is readable through the ordinary route", code == 200, f"HTTP {code}")

        curl("-o", "/dev/null", *auth, "-X", "DELETE", f"{BASE}/api/dashboard/categories/{slug}")
        code, _ = curl("-o", "/dev/null", *auth, "-X", "DELETE",
                       f"{BASE}/api/dashboard/categories/{cid}/permanent")
        check("permanent delete answers 204", code == 204, f"HTTP {code}")
        code, body = curl(*auth, f"{BASE}/api/dashboard/categories/deleted")
        check("the row is gone from the trash bin after permanent delete",
              cid not in [r["id"] for r in ((body_json(body).get("data") or {}).get("content") or [])])
    finally:
        psql("delete from login_activities where user_id in "
             f"(select id from users where email = '{PROBE_EMAIL}')")
        psql("delete from refresh_tokens where user_id in "
             f"(select id from users where email = '{PROBE_EMAIL}')")
        psql(f"delete from users where email = '{PROBE_EMAIL}'")
        left_users = psql(f"select count(*) from users where email like 'trashprobe80%'")
        left_rows = psql("select count(*) from categories where slug like 'zz-hc05-probe%'")
        print(f"    cleanup: {left_users} probe user(s), {left_rows} probe category(ies) left")
        check("the probe left no user behind", left_users == "0", left_users)
        check("the probe left no category behind", left_rows == "0", left_rows)


CRITERIA = {"HC-01": hc_01, "HC-02": hc_02, "HC-03": hc_03, "HC-04": hc_04, "HC-05": hc_05}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — run 20261019-container-healthcheck ===")
    try:
        CRITERIA[name]()
    except Exception as exc:  # a harness error is a failed criterion, never a silent pass
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
