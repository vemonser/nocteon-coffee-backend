# Flyway Migration Inventory & Integrity Audit — Change Contract

Task: Read-only investigation of `src/main/resources/db/migration/` — produce a
version inventory and prove there are no duplicate Flyway versions and no
non-conforming filenames. 33 migration files observed at checkpoint 2.

Source of truth: this file.

## Scope
- Inventory every `V<version>__<name>.sql` file with its parsed version segments and description.
- Check for duplicate Flyway versions (with Flyway semantics: `V106_1` = version 106.1, so `V106` and `V106_1` are distinct).
- Check every filename conforms to `V\d+(_\d+)*__[A-Za-z0-9_]+\.sql`.
- Record the Factory verification-discovery output for this repo in the run artifacts.

## Non-goals
- No migration execution, no `mvn flyway:*` (repair/validate/migrate), no DB connection.
- No application source changes, no change to any existing project file.
- No full test-suite run (explicitly out of safety scope for this investigation).
- No refactor of migration numbering (V111/V119/V121 gaps are valid in Flyway and explicitly out of scope).

## Acceptance criteria
AC-01: A machine-readable inventory `migration-inventory.tsv` exists listing every migration file with `version", "segments`, `description`; row count equals the number of migration files found (33 at planning time, asserted dynamically).
AC-02: No two migration files resolve to the same Flyway version — checker exits 0, and on failure lists the colliding files with paths.
AC-03: Every migration filename matches `^V[0-9]+(_[0-9]+)*__[A-Za-z0-9_]+\.sql$` — checker exits 0, and on failure lists non-conforming names.
AC-04: The verification-discovery output recorded in this run resolves to a real test workflow (`./mvnw test`). It is **not** executed as part of this read-only task; no criterion claims tests passed.
AC-05: Compared against the recorded baseline (3 untracked entries — two pre-existing: the older `20261026-141419` run dir and the root `artifacts/` — plus this run dir itself), `git status --porcelain` shows no modified/added/deleted entries on tracked files, no new untracked entries other than this run directory, HEAD unchanged, and every pre-existing untracked entry preserved.

## Risk / blast radius
Risk: LOW — read-only analysis, artifacts confined to this run directory.
Blast radius: LOCAL — no shared consumers; application and data untouched.

## Validation plan
Deterministic checkers inside this run dir, executed through `evidence-gate record`
so their that exit codes bind to the manifest. Independent adversarial review by
the read-only reviewer; permission surface confirmed by config + earlier runtime probes.

## Rollback
Delete this run directory; nothing else was or can be modified. No gating needed
(analysis-only).
