# Repository Cleanup + Notification Completeness — Change Contract

Task: Remove clearly disposable files, prove the notification feature end-to-end, and fix the duplicate fan-out path so one conflicting row cannot block the rest of the batch.

Source of truth: this file.

## Scope
- Delete `src/test/resources/baseline-hygiene-report.md` (a one-off test report that does not belong in test resources) and the stray root `artifacts/` directory (superseded logs).
- Add integration coverage for notification operations that the pagination suite does not cover: ownership scoping, unread filtering/counting, mark read/unread/all, delete, and page-mode pagination.
- Replace the listener's `save()` + caught `DataIntegrityViolationException` with an `INSERT ... ON CONFLICT DO NOTHING` repository method, so a duplicate notification is skipped without poisoning the transaction for other recipients.
- Keep project documentation (`readme.md`, API contracts, proposals, reports) and `.quality` evidence runs.

## Non-goals
- No Flyway restructuring in this cycle (separate cycle).
- No change to notification API paths, permissions, or response shapes.
- No change to the unique key columns or retention behavior.

## Acceptance criteria
AC-01: Given a notification owned by another user, when mark-read/mark-unread/delete is called, then it throws `NotificationNotFoundException` and leaves the row untouched.
AC-02: Given unread and read notifications, when list/unread-count/mark-read/mark-unread/mark-all-read/page-mode are exercised, then counts and filters match the stored state.
AC-03: Given the same notification key is inserted twice, when the second insert runs, then it is ignored and a subsequent insert for a different key still succeeds in the same transaction.
AC-04: The full existing suite still passes.
AC-05: The two disposable paths are gone and all documentation/evidence files remain.

Risk: MEDIUM
Blast radius: notifications listener + repository + notification tests; no schema change
Validation: targeted notification tests, then full Maven suite; evidence gate
Rollback: revert commit; no DB or schema changes
