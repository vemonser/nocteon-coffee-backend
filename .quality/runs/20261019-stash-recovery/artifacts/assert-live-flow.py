#!/usr/bin/env python3
"""Re-checks the captured live-run artifacts for one acceptance criterion.

The artifacts in this directory were produced by a real HTTP run against the
recovered application on localhost:8081 (see transcript.log). This script does
not re-hit the network; it asserts on the recorded responses so the evidence
gate has a deterministic, re-runnable exit code.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name):
    return json.loads((HERE / name).read_text())


def transcript():
    return (HERE / "transcript.log").read_text()


def fail(msg):
    print(f"FAIL: {msg}")
    sys.exit(1)


def ok(msg):
    print(f"PASS: {msg}")


def ac01():
    before = load("01-get-deleted-before.json")
    if before.get("success") is not True:
        fail(f"GET /deleted returned success={before.get('success')}")
    if before.get("message") != "Deleted categories retrieved":
        fail(f"unexpected message: {before.get('message')!r}")
    if before.get("data") is None or "content" not in before["data"]:
        fail("response has no paged content")
    ok("GET /api/dashboard/categories/deleted -> 200, paged trash listing "
       f"({len(before['data']['content'])} rows), not 'Category not found'")
    if "GET /api/dashboard/categories/deleted (before)\nHTTP 200" not in transcript():
        fail("transcript does not show HTTP 200 for the trash listing")
    ok("transcript confirms HTTP 200")


def ac02():
    created = load("02-create.json")["data"]
    probe = created["id"]
    trash = load("04-get-deleted-contains.json")["data"]["content"]
    if probe not in [row["id"] for row in trash]:
        fail("soft-deleted probe did not appear in the trash listing")
    ok(f"soft-deleted id={probe} appears in the trash listing")

    restore = load("05-restore.json")
    if restore.get("success") is not True or restore.get("message") != "Category restored":
        fail(f"restore did not succeed: {restore}")
    ok("PATCH /{id}/restore -> 200 'Category restored'")

    after = load("06-get-deleted-after-restore.json")["data"]["content"]
    if probe in [row["id"] for row in after]:
        fail("restored row is still in the trash listing")
    ok("restored row left the trash listing")

    readable = load("07-get-category-after-restore.json")
    if readable.get("success") is not True or (readable.get("data") or {}).get("slug") != created["slug"]:
        fail(f"restored row is not readable through the ordinary route: {readable}")
    ok("restored row is readable through GET /api/dashboard/categories/{slug}")


def ac03():
    created = load("02-create.json")["data"]
    probe = created["id"]
    gone = load("10-verify-gone.json")["data"]["content"]
    if probe in [row["id"] for row in gone]:
        fail("permanently deleted row is still listed")
    ok(f"permanently deleted id={probe} is absent from the trash listing")
    if "permanent HTTP 204" not in transcript():
        fail("transcript does not show 204 for the permanent delete")
    ok("transcript confirms DELETE /{id}/permanent -> 204")


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else ""
    handlers = {"AC-01": ac01, "AC-02": ac02, "AC-03": ac03}
    if which not in handlers:
        print(f"usage: {sys.argv[0]} {{AC-01|AC-02|AC-03}}")
        sys.exit(2)
    handlers[which]()
    print(f"RESULT: {which} PASS")
