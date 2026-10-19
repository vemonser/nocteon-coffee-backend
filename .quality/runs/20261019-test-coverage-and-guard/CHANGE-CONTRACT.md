# CHANGE-CONTRACT — F1 + F2: test coverage and pagination regression guard

Run: 20261019-test-coverage-and-guard
Base commit: 5322bfe (after the #1..#10 commits)

## Why

Two follow-ups recorded during the queue, neither a defect:

- **F2 (regression guard for item #2).** The conclusion that `findAllDashboard` paginates in SQL
  (not in memory) is true on Hibernate 7.4.1 but nothing in the build would notice if it
  flipped back. A downgrade across the 6.1/6.2 boundary, or a refactor, would silently
  read the whole table.
- **F1 (test coverage gap).** The 555-test suite is unit tests with Mockito; trash listings
  use native queries that need a real DB, and no integration test existed for any trash flow
  (the comment in `JournalService` referenced a non-existent
  `SoftDeleteRepositoryIntegrationTest`). Casts such as `JournalPostStatus.valueOf((String) row[4])`
  only fail on a real JDBC row.

## Scope

- **F2** Add `hibernate.query.fail_on_pagination_over_collection_fetch=true` (config, 1 line)
  so in-memory pagination throws instead of warning, plus `ProductPaginationGuardTest` which
  asserts via a `StatementInspector` (`SqlCapture`) that the generated SQL carries the limit
  inside the distinct sub-query, before the pairings join.
- **F1** Add `TrashBinIntegrationTest` (`@SpringBootTest`, real DB) covering the Product,
  Category and Journal (enum-cast) trash flows end-to-end.

## Non-goals

- No change to production code beyond the single config line.
- No Testcontainers (reuse the dev DB, consistent with `NocteonApiApplicationTests`).

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| PG-01 | Given `fail_on_pagination_over_collection_fetch=true`, when the app boots, then the context loads | `NocteonApiApplicationTests` |
| PG-02 | Given `findAllDashboard(page=0,size=5)`, when the SQL is captured, then it contains `offset`/`fetch first` inside the distinct sub-query, before the pairings join | `ProductPaginationGuardTest` |
| PG-03 | Given `findAllDashboard(page=1,size=5)`, when the SQL is captured, then it still paginates in SQL (non-zero offset) | `ProductPaginationGuardTest` |
| TI-01 | Given a soft-deleted product, when the trash listing is called, then it appears and disappears after restore | `TrashBinIntegrationTest.productTrashFlow` |
| TI-02 | Given a soft-deleted category, when the trash listing is called, then it appears and disappears after restore | `TrashBinIntegrationTest.categoryTrashFlow` |
| TI-03 | Given a seeded trashed journal post with status PUBLISHED, when the trash listing is called, then the row appears with `status = "PUBLISHED"` (enum cast succeeded on a real JDBC row) | `TrashBinIntegrationTest.journalPostTrashFlowExercisesEnumCast` |
| B-01 | Given all changes, `./mvnw compile` and `./mvnw test` pass | build logs |

## Affected areas / blast radius

| Area | Effect |
|---|---|
| `application.yaml` | +1 config line (safety net) |
| `src/test/.../support/SqlCapture.java` | new test utility |
| `src/test/.../product/ProductPaginationGuardTest.java` | new guard test |
| `src/test/.../trash/TrashBinIntegrationTest.java` | new integration tests |

Risk: **LOW** (test code + one config line; app boots verified). Blast radius: test suite + boot.

## Validation plan

1. `./mvnw test -Dtest=ProductPaginationGuardTest,TrashBinIntegrationTest,NocteonApiApplicationTests`
2. `./mvnw test` (full suite)
3. Evidence Gate over `evidence-manifest.json`

## Rollback

Revert the config line and delete the 3 test files. No production code change.
