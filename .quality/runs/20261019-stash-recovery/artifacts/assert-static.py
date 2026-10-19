#!/usr/bin/env python3
"""Static assertions over the recovered worktree.

AC-04: every catalog the stash covered still exposes the three trash-bin routes.
AC-06: the files the worktree had moved on independently were not reverted, and
       Role.java carries both the stash's and the worktree's edits.
"""
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]

# sha256 of the pre-recovery (2026-10-19 08:41) content of the preserved files
EXPECTED_SHA256 = {
    "docker-compose.yml": "a5afd96e7ac1c65415546cb550ce295275b5af9e5bb05c6ca8306294adcf7ab8",
    "pom.xml": "aa4c73d5512f01c3a00bb516289b4920d198a7a3d9b7f53ee552d573837fd5f1",
    "src/main/java/com/nocteon/nocteon_api/common/enums/Permission.java":
        "c0ba04dcdf94b115a527bb8c48f035fd03a4d128626cb57815b637566fa6a595",
    "src/main/resources/messages.properties":
        "ca94ccdd52432f059aa4756df871b01998f71a5aa00901d7610e9c59df371f03",
    "src/main/resources/messages_ar.properties":
        "13074c14b3f1302c0610b9108b0567f8cf1191ca033d61902e673f0eb8784e9a",
    "src/main/resources/db/migration/V3__create_product_tables.sql":
        "34a0a9e3baf9f57dd33cd43c8205a2347b8862d6ca68fee898586c658da3d8f1",
    "src/main/resources/db/migration/V4__create_commerce_and_promo_code_tables.sql":
        "ac630dda50063f57efd2c6a694477230cbfdabd66951e0f84bfb6fa3e9728da8",
}

# resource path -> controller file that must map all three routes
ENTITIES = {
    "/dashboard/farms": "farm/controller/FarmController.java",
    "/dashboard/origins": "origin/controller/OriginController.java",
    "/dashboard/pairings": "pairing/controller/PairingController.java",
    "/dashboard/brewing-methods": "brewingMethod/controller/BrewingMethodController.java",
    "/dashboard/processing-methods": "processingMethod/controller/ProcessingMethodController.java",
    "/dashboard/roast-levels": "roastLevel/controller/RoastLevelController.java",
    "/dashboard/tasting-notes": "tastingNote/controller/TastingNoteController.java",
    "/dashboard/coffee-varieties": "coffeeVariety/controller/CoffeeVarietyController.java",
    "/dashboard/journal": "journal/controller/JournalController.java",
    "/dashboard/products": "product/controller/ProductController.java",
}

SRC = ROOT / "src/main/java/com/nocteon/nocteon_api"


def fail(msg):
    print(f"FAIL: {msg}")
    sys.exit(1)


def ok(msg):
    print(f"PASS: {msg}")


def ac04():
    missing = []
    for base, rel in ENTITIES.items():
        path = SRC / rel
        if not path.is_file():
            missing.append(f"{base}: controller missing ({rel})")
            continue
        text = path.read_text()
        for needle, label in (
            (f'@GetMapping("{base}/deleted")', "GET /deleted"),
            (f'@PatchMapping("{base}/{{id}}/restore")', "PATCH /{id}/restore"),
            (f'@DeleteMapping("{base}/{{id}}/permanent")', "DELETE /{id}/permanent"),
        ):
            if needle not in text:
                missing.append(f"{base}: {label} not mapped")
    if missing:
        for m in missing:
            print(f"  - {m}")
        fail(f"{len(missing)} trash-bin route(s) missing across the catalogs")
    ok(f"all three trash-bin routes present for {len(ENTITIES)} entity surfaces")


def ac06():
    for rel, want in EXPECTED_SHA256.items():
        got = hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()
        if got != want:
            fail(f"{rel} changed vs the pre-recovery copy (sha256 {got})")
    ok(f"{len(EXPECTED_SHA256)} independently-edited files are byte-identical to pre-recovery")

    role = (ROOT / "src/main/java/com/nocteon/nocteon_api/auth/enums/Role.java").read_text()
    moderator = role.split("CUSTOMER(Set.of(")[0]
    customer = role.split("CUSTOMER(Set.of(")[1]
    if "Permission.DASHBOARD_READ" not in moderator:
        fail("stash edit lost: MODERATOR no longer has DASHBOARD_READ")
    if "Permission.AUDIT_READ" not in moderator:
        fail("worktree edit lost: MODERATOR no longer has AUDIT_READ")
    if "Permission.USER_READ" not in customer or "Permission.USER_UPDATE" not in customer:
        fail("CUSTOMER lost USER_READ/USER_UPDATE, which 403s the whole /api/addresses API")
    ok("Role.java: MODERATOR has DASHBOARD_READ + AUDIT_READ; "
       "CUSTOMER keeps USER_READ/USER_UPDATE so /api/addresses still works")


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else ""
    handlers = {"AC-04": ac04, "AC-06": ac06}
    if which not in handlers:
        print(f"usage: {sys.argv[0]} {{AC-04|AC-06}}")
        sys.exit(2)
    handlers[which]()
    print(f"RESULT: {which} PASS")
