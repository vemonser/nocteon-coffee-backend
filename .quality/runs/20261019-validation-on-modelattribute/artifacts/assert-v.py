#!/usr/bin/env python3
"""Assertion harness for item #5: @Valid on @ModelAttribute + BindException handler."""

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
def v_01() -> None:
    """Invalid params -> 400 with field errors, not 500."""
    token = probe.provision_token(BASE)
    try:
        code, body = call(token, "GET", "/api/dashboard/categories/deleted?page=-1&size=5")
        errs = body.get("errors") or []
        check("page=-1 -> HTTP 400 (was 500)", code == 400, f"HTTP {code}")
        check("a field error names 'page'", any(e.get("field") == "page" for e in errs), str(errs))

        code, body = call(token, "GET", "/api/dashboard/categories/deleted?page=0&size=0")
        errs = body.get("errors") or []
        check("size=0 -> HTTP 400", code == 400, f"HTTP {code}")
        check("a field error names 'size'", any(e.get("field") == "size" for e in errs), str(errs))

        code, body = call(token, "GET", "/api/dashboard/categories/deleted?page=0&size=101")
        check("size=101 -> HTTP 400", code == 400, f"HTTP {code}")
    finally:
        probe.cleanup()


# --------------------------------------------------------------------------- #
def v_02() -> None:
    """Valid params -> 200."""
    token = probe.provision_token(BASE)
    try:
        code, body = call(token, "GET", "/api/dashboard/categories/deleted?page=0&size=5")
        check("valid params -> HTTP 200", code == 200, f"HTTP {code}")
        check("message is the trash listing", body.get("message") == "Deleted categories retrieved",
              repr(body.get("message")))
    finally:
        probe.cleanup()


# --------------------------------------------------------------------------- #
def v_03() -> None:
    """Static: every @ModelAttribute is now @Valid-ated; BindException is handled."""
    unvalidated = 0
    for root, _, files in os.walk(os.path.join(ROOT, "src/main/java")):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            p = os.path.join(root, fn)
            lines = open(p, encoding="utf-8").read().splitlines()
            for i, l in enumerate(lines):
                stripped = l.strip()
                if stripped.startswith("@ModelAttribute"):
                    prev = lines[i - 1].strip() if i > 0 else ""
                    if prev != "@Valid":
                        unvalidated += 1
                        print(f"    UNVALIDATED: {os.path.relpath(p, ROOT)}:{i + 1}")
    check("every @ModelAttribute is preceded by @Valid", unvalidated == 0,
          f"{unvalidated} un-validated")

    gh = os.path.join(ROOT, "src/main/java/com/nocteon/nocteon_api/common/exception/GlobalExceptionHandler.java")
    src = open(gh, encoding="utf-8").read()
    check("BindException is imported", "import org.springframework.validation.BindException;" in src)
    check("BindException has a handler", "@ExceptionHandler(BindException.class)" in src)

    # @Valid import present in all controller files that use @ModelAttribute
    missing_import = 0
    for root, _, files in os.walk(os.path.join(ROOT, "src/main/java")):
        for fn in files:
            if not fn.endswith("Controller.java"):
                continue
            p = os.path.join(root, fn)
            text = open(p, encoding="utf-8").read()
            if "@ModelAttribute" in text and "import jakarta.validation.Valid;" not in text:
                missing_import += 1
                print(f"    MISSING IMPORT: {os.path.relpath(p, ROOT)}")
    check("@Valid imported in every controller using @ModelAttribute", missing_import == 0,
          f"{missing_import} missing")


CRITERIA = {"V-01": v_01, "V-02": v_02, "V-03": v_03}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in CRITERIA:
        print(f"usage: {sys.argv[0]} {{ {' | '.join(CRITERIA)} }}", file=sys.stderr)
        sys.exit(2)
    name = sys.argv[1]
    print(f"=== {name} — item #5 (@Valid on @ModelAttribute) ===")
    try:
        CRITERIA[name]()
    except Exception as exc:
        print(f"  [FAIL] harness error: {type(exc).__name__}: {exc}")
        FAILURES.append(f"harness error: {exc}")
    print(f"=== {name}: {'PASS' if not FAILURES else 'FAIL'} ({len(FAILURES)} failing assertion(s)) ===")
    for f in FAILURES:
        print(f"    - {f}")
    sys.exit(0 if not FAILURES else 1)
