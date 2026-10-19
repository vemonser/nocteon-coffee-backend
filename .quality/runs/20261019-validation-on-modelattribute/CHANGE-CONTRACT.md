# CHANGE-CONTRACT — Item #5: `@Valid` on `@ModelAttribute` filter params

Run: 20261019-validation-on-modelattribute
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb

## Why

Every dashboard/list controller binds its filter with `@ModelAttribute XFilter filter` and
relies on `@Min`/`@Max`/`@Size` on the DTO — but **none** of them annotate the parameter
with `@Valid`. Spring therefore skips bean validation and feeds out-of-range values
straight into `PageRequest.of(page, size)` / `subList`, which throws
`IllegalArgumentException` and surfaces as a **500** instead of a **400**.

Reproduction: `GET /api/dashboard/categories/deleted?page=-1&size=5` → 500.
Expected: 400 with a field error naming `page`.

The defect is repo-wide: 54 `@ModelAttribute` usages across 22 files, none validated.

A second gap: even with `@Valid` in place, `@ModelAttribute` binding failures throw
`BindException`, not `MethodArgumentNotValidException`, and `GlobalExceptionHandler`
only handled the latter — so validation would still 500. A `BindException` handler
(mirroring `handleValidation`) is required to close the loop.

## Scope

- Add `@Valid` before every `@ModelAttribute` parameter in controllers (54 usages,
  21 controllers + `AdminUserController`).
- Add a `BindException` handler to `GlobalExceptionHandler` returning 400 with field
  errors.
- Ensure `jakarta.validation.Valid` is imported in every touched controller.

## Non-goals

- No change to the filter DTOs or their validation annotations.
- No change to `MethodArgumentNotValidException` handling (still covers `@RequestBody`).
- No change to the pagination/clamping logic itself.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| V-01 | Given invalid params (`page=-1`, `size=0`, `size=101`), when a `/deleted` endpoint is called, then it answers 400 with a field error naming the offending field | live on `:8080` |
| V-02 | Given valid params, when the same endpoint is called, then it answers 200 | live on `:8080` |
| V-03 | Given the codebase, when inspected, then every `@ModelAttribute` is preceded by `@Valid`, every such controller imports `jakarta.validation.Valid`, and `GlobalExceptionHandler` handles `BindException` | static scan |
| V-04 | Given the changes, when `./mvnw compile` and `./mvnw test` run, then both pass | build logs |

## Affected areas / blast radius

| Area | Effect |
|---|---|
| 21 controllers + `AdminUserController` | `@Valid` added; invalid filter params now 400 instead of 500 |
| `GlobalExceptionHandler` | new `BindException` handler → 400 with field errors |

Risk: **LOW-MEDIUM** (behaviour change: previously-accepted invalid params now rejected
with 400; any client sending `page=-1`/`size=0` will see a 400 instead of a 500).
Blast radius: every dashboard list endpoint.

## Validation plan

1. `python3 artifacts/assert-v.py V-01 … V-03`
2. `./mvnw compile` (exit 0) and `./mvnw test` (555 tests, 0 failures)
3. Evidence Gate over `evidence-manifest.json`

## Rollback

Revert the `@Valid` additions and the `BindException` handler. Validation annotations on
the DTOs remain (harmless without `@Valid`).
