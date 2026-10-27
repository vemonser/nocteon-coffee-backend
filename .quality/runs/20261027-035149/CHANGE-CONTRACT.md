# Java 25 LTS Migration — Change Contract

Task: Move the build, test, container and deployment surfaces from Java 21 to Java 25 LTS, verify the full suite on JDK 25, and prove the Docker stack still boots and reaches PostgreSQL and Redis.

Source of truth: this file.

## Scope
- `.mise.toml`: project JDK 25.
- `pom.xml`: `java.version=25`; Lombok 1.18.46 (first JDK 25-capable line; 1.18.48 has a known `@Builder` regression).
- CI: Temurin 25 and an updated rationale comment.
- Dockerfile: build/runtime images Temurin 25; replace the missing `wget` healthcheck with a `bash` `/dev/tcp` probe.
- `render.yaml`: `JAVA_VERSION=25` for Render's native Java runtime.
- No application-code changes, no schema changes.

## Non-goals
- No Render deployment switch to Docker (native runtime remains, with `JAVA_VERSION`).
- No Spring Boot version bump.
- No Java 25 language-feature adoption.

## Acceptance criteria
AC-01: With JDK 25, `./mvnw clean test` compiles and passes all 730 tests on a freshly migrated database.
AC-02: The rebuilt Docker image reports Java 25, reaches PostgreSQL and Redis, becomes healthy, and `GET /api/settings` returns 200.
AC-03: Every build/deploy touchpoint targets 25: pom.xml, .mise.toml, CI and the Dockerfile; Render uses that same Temurin 25 Docker image; no Java 21/Lombok 1.18.36 reference remains.

Risk: MEDIUM
Blast radius: build, test, container and deployment toolchain; application code unchanged
Validation: JDK 25 compile, full suite, Docker build + compose health smoke, static version check
Rollback: revert commit; the Java 21 image and config remain in git history

## Known operational note
The running app container was rebuilt during validation and is healthy on Temurin 25. Local `.env` JWT keys were generated for the smoke test and are gitignored.
