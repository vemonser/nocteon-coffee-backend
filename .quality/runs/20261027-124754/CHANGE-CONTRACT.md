# P0 Backend Hardening — Change Contract

Task: Remove the duplicate Bucket4j classpath, move noisy/PII SQL logging out of production, disable open-in-view, make CORS parsing safe, and trim Docker build context.

Source of truth: this file.

## Scope
- Remove the stray `bucket4j_jdk17-core:8.19.0`; keep the `bucket4j-redis`/`bucket4j-core` 8.10.1 pair the code is written against.
- Add `application-prod.yaml`: Hibernate SQL and bind levels INFO, app/security/Flyway INFO, SpringDoc disabled.
- Activate the prod profile in `render.yaml`.
- Set `spring.jpa.open-in-view=false`.
- `CorsConfig`: trim origins, drop blank entries, fail fast on an empty list.
- Add `.quality/` and `artifacts/` to `.dockerignore`.
- Tests: `CorsConfigTest`, `ProductionProfileConfigTest`; full suite and Docker smoke unchanged.

## Non-goals
- No change to business logic or API contracts.
- No Spring Boot patch/minor bump in this cycle.
- No scheduler locking or notification fan-out optimisation (P1).

## Acceptance criteria
AC-01: `dependency:tree` contains only the 8.10.1 Bucket4j artifacts and no `bucket4j_jdk17-core`; the full suite still passes.
AC-02: With the `prod` profile active, Hibernate SQL/bind are INFO, SpringDoc is disabled, and `open-in-view` is false; `ProductionProfileConfigTest` passes.
AC-03: CORS origins are trimmed and blank entries dropped, and an empty list fails fast; `CorsConfigTest` passes.
AC-04: The Docker image still builds, is healthy, reaches Redis, `/api/settings` and readiness return 200.
AC-05: `.dockerignore` excludes `.quality/` and `artifacts/`, and the full JDK 25 suite passes (741 tests).

Risk: MEDIUM
Blast radius: dependency resolution, production logging, JPA fetch semantics, CORS, Docker build context
Validation: dependency tree, focused tests, full suite, Docker compose health smoke
Rollback: revert commit; no schema/data change

## Known operational note
Production logging is now controlled by the `prod` profile; local Docker still runs the default profile and keeps SQL DEBUG/TRACE for development.
