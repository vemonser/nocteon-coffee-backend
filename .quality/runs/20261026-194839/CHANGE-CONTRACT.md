# Duplicate Live Translation Name Protection — Change Contract

Task: Extend Category's duplicate live-translation-name protection and restore conflict checks to the remaining lookup entities (Origin, Farm, BrewingMethod, CoffeeVariety, ProcessingMethod, RoastLevel, TastingNote, Pairing).

Source of truth: this file.

## Scope
- Add `existsLiveTranslationName(...)` queries to each entity's translation repository.
- Add duplicate-name checks in create/update paths for each entity, throwing `DuplicateTranslationException` on conflict.
- Add restore pre-checks for each soft-deletable entity so conflicting restores return 409 instead of 500.
- Add `existsBySlugIncludingDeleted(...)` and an advisory slug lock to every slug-bearing lookup entity (Origin, Farm, BrewingMethod, CoffeeVariety, ProcessingMethod, RoastLevel, TastingNote, Pairing), mirroring Category. Required because the duplicate guard deliberately ignores soft-deleted rows, so a name may be reused after a trash; the plain UNIQUE `slug` constraint would otherwise fail with a 500.
- Add targeted tests for Origin and Farm plus a combined reuse/restore test for the remaining six, and cap the test Hikari pool size so 20+ cached Spring contexts cannot exhaust PostgreSQL `max_connections`.

## Non-goals
- No changes to Category (already implemented).
- No changes to Product/ProductOption duplicate handling.
- No schema migration for partial unique indexes (application-level checks only, matching Category).

## Acceptance criteria
AC-01: Given a dashboard create/update where a translation name (case/space-insensitive) is already used by a live entity of the same type in the same language, when the service executes, then it throws `DuplicateTranslationException` (mapped to HTTP 409, `error.duplicate.translation`).
AC-02: Given a trashed entity whose name now conflicts with a live entity, when restore is called, then it throws `DuplicateTranslationException` (HTTP 409) instead of failing with a 500.
AC-03: Existing create/update/restore tests continue to pass (full suite green).
AC-04: New targeted tests for Origin and Farm duplicate-name and restore-conflict behavior pass.
AC-05: Given an entity was soft-deleted, when a new entity reuses its name, then creation succeeds with a suffixed slug (no `slug` unique-constraint 500).
AC-06: Given two concurrent creates slugify to the same base, then slug generation is serialized and both rows get distinct slugs.

Risk: MEDIUM
Blast radius: LOCAL (lookup modules + their tests)
Validation: targeted Maven tests for affected modules, then full Maven suite
Rollback: revert commit; no DB state changes
