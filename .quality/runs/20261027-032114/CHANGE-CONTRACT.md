# Flyway Baseline Consolidation — Change Contract

Task: Replace the accumulated V1..V124 migration chain with a per-domain final-schema baseline, move demo data into a dev/test-only `db/seed` location, recover the roles/permissions tables that existed only as Java migrations, and validate the result on a genuinely empty database.

Source of truth: this file.

## Scope
- Replace `db/migration` with `V1..V11`:
  - V1 extensions (guarded pgvector), V2 auth, V3 catalog, V4 commerce, V5 journal,
    V6 notifications, V7 broadcasts/contact, V8 shipping/settings, V9 audit,
    V10 all foreign keys, V11 operational reference data.
- Each table's final definition (columns, sequences, defaults, constraints, indexes)
  lives in its domain file; `ALTER`s are no longer scattered outside it.
- Move demo data (catalog, products, users, orders, reviews, login activity,
  notifications, newsletter) to `db/seed/V100..V116`, excluded in production.
- Keep required operational data in V11: roles, role_permissions, store_settings,
  shipping_zones, shipping_zone_cities.
- Delete the Java migrations `src/main/java/db/migration/V119` and `V121`; their
  schema and data are now in V2/V11.
- Default local/test Flyway locations include `db/seed`; production sets
  `SPRING_FLYWAY_LOCATIONS=classpath:db/migration` in `render.yaml`.
- Update `scripts/rebuild-database.sh` and add `db/README.md`.
- Add `FlywayMigrationLayoutTest` to reject duplicate versions, range overlap,
  empty migrations, or missing roles tables.

## Non-goals
- No business-logic changes and no entity/DTO changes.
- No seed-content redesign beyond removing duplicated reference inserts and
  legacy status backfills that overwrote the seed-defined review statuses.
- No production data migration; the existing database is disposable and must be
  rebuilt (documented, not executed here).

## Acceptance criteria
AC-01: Given an empty PostgreSQL database, when Flyway runs with schema + seeds, then 29 migrations apply and the full test suite (728 tests) passes.
AC-02: Given an empty PostgreSQL database, when Flyway runs with schema only (production locations), then 11 migrations apply, the context boots with Hibernate `ddl-auto=validate`, and demo tables are empty while roles/permissions/settings/shipping reference data is present.
AC-03: The schema-only dump of the freshly migrated database is identical to the previous database schema, ignoring the pg_dump `\restrict` token and the runtime-managed `vector_store` table.
AC-04: `roles` and `role_permissions` are created in V2 and seeded in V11; the layout test fails on duplicate versions, overlapping ranges, empty files, or missing role tables.
AC-05: The documented rebuild path (`scripts/rebuild-database.sh`) applies the new layout end-to-end on a disposable database.

Risk: HIGH
Blast radius: GLOBAL (entire database schema and startup)
Validation: fresh template database for schema+seeds, separate fresh database for production locations, schema diff vs old database, layout test, full suite
Rollback: revert the branch/commit and restore the previous migration files from git history; no production data was touched

## Known operational note
An existing database with the old `flyway_schema_history` cannot validate against the new chain. Rebuild it with `scripts/rebuild-database.sh`; this is intentional and documented in `src/main/resources/db/README.md`.
