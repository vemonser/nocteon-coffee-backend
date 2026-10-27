# Quick-task plan — Flyway migration inventory & integrity audit

Plan file for checkpoint 4 of `/factory/run` (small tracked work; the repo has no
`.planning/`, so a full GSD phase plan does not apply — see the run command's
small-tracked task route).

Steps:
1. [x] Explore: enumerate migration files; confirm scope (33 files, `application.yaml`, no flyway config overrides).
2. [x] Record pre-run baseline (HEAD + porcelain status) in this run dir.
3. [ ] Write deterministic audit/checker scripts (in-run-dir only).
4. [ ] Run `verify-discovery.cjs`; record RESOLVED + exit code (AC-04).
5. [ ] Run inventory generator; produce `migration-inventory.tsv` + `REPORT.md` (AC-01).
6. [ ] Run duplicate-version checker (AC-02) and filename-conformance checker (AC-03).
7. [ ] Run repo-cleanliness checker against baseline (AC-05).
8. [ ] Assert no pending review blockers; at least review cleanliness — confirm the reviewer session left no writes: `git status --porcelain` vs baseline again after review.
9. [ ] Write evidence manifest; `record` each criterion; `validate` — target exit 0.
10. [ ] Handoff report.
