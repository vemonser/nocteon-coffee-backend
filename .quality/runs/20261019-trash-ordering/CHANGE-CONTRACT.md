# CHANGE-CONTRACT — Items #3 and #4: trash-listing ordering

Run: 20261019-trash-ordering
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb

## Why

The trash-bin listings load **all** deleted rows into memory and slice them with
`subList(fromIndex, toIndex)`. That slicing is only safe if the underlying list has a
**stable, deterministic order**. Two defects broke that:

1. **The native queries carry no `ORDER BY`.** `findDeletedBySearchNative` (and
   `findAllDeletedNative`) return rows in whatever order PostgreSQL happens to produce
   — unspecified and not guaranteed to be stable across executions. Each page request
   re-runs the query and re-slices, so two calls for page 0 / page 1 can overlap or
   leave gaps when the row order shifts.
2. **The in-memory sort does not cover the default sort key.** `BaseFilterRequest.sort`
   defaults to `"createdAt"` (desc). `ProductService.getAllDeleted` and
   `PromoCodeService.getAllDeleted` had **no `createdAt` case** — `result = 0` — so the
   default request silently did nothing and left the list in DB order. `JournalService`
   handled `createdAt` for posts but `getAllDeletedCategories` ignored `sort` and
   `direction` altogether (item #4).

Root cause is the missing deterministic order, not the slicing itself.

## Scope

- Add `ORDER BY … created_at DESC, <language>` to the native trash queries of
  Product, Category, JournalPost and JournalCategory (both `findAllDeletedNative` and
  `findDeletedBySearchNative`).
- Add a `createdAt` comparator case to `ProductService` and `PromoCodeService`, which
  requires projecting `createdAt` onto `DashboardProductResponse` and
  `PromoCodeResponse` (additive, backward-compatible fields) and populating them in
  both mappers/builders.
- Make `JournalService.getAllDeletedCategories` honour `sort` and `direction` like the
  post listing (item #4).
- Verify no regression: compile + full test suite (555 tests).

## Non-goals

- No change to the live (non-deleted) listing path.
- No change to the slicing/clamping logic.
- No change to the `@SQLRestriction` behaviour or to soft-delete itself.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| TR-01 | Given the native trash queries, when inspected, then every one of Product, Category, JournalPost and JournalCategory carries `ORDER BY … created_at` | static grep over the 4 repositories |
| TR-02 | Given the Product trash listing, when `sort=createdAt` is requested in either direction, then the result is ordered accordingly (newest-first by default, ascending reverses) | live on `:8080` with distinct timestamps |
| TR-03 | Given the Product trash listing, when the same request is issued twice, then the order is byte-identical (no repeat/drop) | live on `:8080` |
| TR-04 | Given the Journal-category trash listing, when `sort`/`direction` is supplied, then it is honoured (was previously ignored) | live on `:8080` with a seeded trashed category |
| TR-05 | Given the changes, when `./mvnw compile` and `./mvnw test` run, then both pass | build logs |

## Affected areas / blast radius

| Area | Effect |
|---|---|
| 4 repository interfaces | +`ORDER BY` on 8 native queries (additive, deterministic) |
| `DashboardProductResponse`, `PromoCodeResponse` | +`createdAt` field (additive, backward compatible) |
| `DashboardProductMapper`, `ProductService`, `PromoCodeService` | populate + sort the new field |
| `JournalService.getAllDeletedCategories` | honour sort/direction |

Risk: **LOW-MEDIUM** (additive fields + deterministic ordering; no behaviour change for
clients that ignore the new field). Blast radius: dashboard trash-bin endpoints only.

## Validation plan

1. `python3 artifacts/assert-tr.py TR-01 … TR-04`
2. `./mvnw compile` (exit 0) and `./mvnw test` (555 tests, 0 failures)
3. Evidence Gate over `evidence-manifest.json`

## Rollback

Revert the 4 native-query `ORDER BY` additions and the 2 DTO field additions; the
in-memory sort cases can stay (harmless). No DB or migration change.
