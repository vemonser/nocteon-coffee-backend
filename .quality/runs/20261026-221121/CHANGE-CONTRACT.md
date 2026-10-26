# Reject Null/Blank Translation Names — Change Contract

Task: Make `LookupServiceHelper.validateTranslations` reject `null`, empty, and whitespace-only translation names so a bad name can never reach the duplicate-name guard (which treats SQL `NULL` as "no conflict") or the `NOT NULL` column and produce a 500.

Source of truth: this file.

## Scope
- Add a null/blank name check to `LookupServiceHelper.validateTranslations`, throwing the existing `InvalidTranslationException` (HTTP 400, `error.translation.invalid`).
- Cover `null`, empty, and whitespace-only (spaces, tabs, newlines) names with unit tests.
- Keep valid-name behavior and all existing language/duplicate-language checks unchanged.

## Non-goals
- No change to stored names (no trimming/rewriting).
- No change to `assertNoDuplicateNames` or the SQL predicates.
- No new validation for descriptions, countries, or other fields.
- No change to controller-level `@Valid`/`@NotBlank` rules.

## Acceptance criteria
AC-01: Given a translation with a null, empty, or whitespace-only `name`, when `validateTranslations` runs, then it throws `InvalidTranslationException` (HTTP 400) before any duplicate or persistence work.
AC-02: Given a list containing a valid `en` and `ar` translation with non-blank names, when `validateTranslations` runs, then it returns normally.
AC-03: Existing service tests continue to pass (full suite green).

Risk: LOW
Blast radius: LOCAL (one shared helper used by 12 services)
Validation: focused unit test for `LookupServiceHelper`, then full Maven suite
Rollback: revert commit; no DB or schema changes
