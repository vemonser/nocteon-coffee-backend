# Backend decisions

This file records decisions that belong to the API and that the dashboard needs to
know about. The dashboard's own `DECISIONS.md` (in the frontend repository) keeps
the UI-side notes.

## AUDIT — the actor is the admin who acted, id and e-mail

_Supersedes the frontend note "Needs backend: there is no username column on audit_logs",
so far as it described the actor as unrecorded._

**What was wrong.** `UserService` (toggle active / block / unblock, change role) and
`RoleService` (create, update permissions, delete) called
`auditLogService.logSuccess(...)` with `null` for both the actor id and the actor
e-mail. The acting principal was available at the controller (and
`UserService.toggleActive` / `changeRole` already received it), but the audit call
dropped it. A row therefore read `USER_BLOCKED | User | null | (empty)` and the
audit page had no choice but "System" — the one question the page exists to answer
("who did this?") was unanswerable for exactly the actions that matter most.

**What is now true.**

- Every audited user/role action records **both** `user_id` and `user_email` of the
  authenticated admin who performed it. Both columns, from one derived pair, so a
  row can never carry half an actor.
- The contract of `GET /api/dashboard/audit-logs` is unchanged: `userId` and
  `userEmail` are still the fields. The page shows the e-mail and falls back to
  `#id`; it shows "System" only when both are absent.
- A genuine system action records no actor at all. Nothing invents one: the
  services take the principal as a parameter, so a caller without one (scheduler,
  internal job, test) writes `null`/`null`. There is no static `SecurityContext`
  read inside these services, so a background job cannot borrow the identity of
  whatever request happens to share its thread.

| Audited action | Recorded actor id | Recorded actor e-mail |
| --- | --- | --- |
| `USER_BLOCKED` | acting admin's `users.id` | acting admin's `users.email` |
| `USER_UNBLOCKED` | acting admin's `users.id` | acting admin's `users.email` |
| `ROLE_CHANGED` (a user's role) | acting admin's `users.id` | acting admin's `users.email` |
| `ROLE_CREATED` | acting admin's `users.id` | acting admin's `users.email` |
| `ROLE_PERMISSIONS_UPDATED` | acting admin's `users.id` | acting admin's `users.email` |
| `ROLE_DELETED` | acting admin's `users.id` | acting admin's `users.email` |

**How it is threaded.** The controllers already pass `@AuthenticationPrincipal
UserPrincipal` into `UserService`; `RoleController` now does the same for create,
update and delete. `common/security/AuditActor.of(principal)` derives the
`(id, email)` pair once (null principal → `NONE`), and the services hand those two
values to the existing `AuditLogService.logSuccess(...)` signature (unchanged).
`ActorResolver` was not reused for this: it returns the label string `"system"`
for display rows (inventory), which must never be stored as an audit e-mail.

**Deliberately not done.**

- **No username column and no migration.** The schema is unchanged. A username is
  mutable and a deleted user keeps no row to join; `user_email` is the snapshot of
  who acted at the moment of the action, which is what an audit trail should keep.
  A migration to add `username` would still not let the page name an actor whose
  account has since been deleted.
- **No backfill of old rows.** Rows written before this fix keep their null actor.
  Backfilling would mean guessing who acted from the current session or the target
  user — invented evidence in a log is worse than a gap, and the gap is honest.
- **No `ip_address` / `user_agent` for these actions.** The services have no access
  to the request. `AuditLogService` accepts both fields, but no caller fills them
  today; adding request plumbing to every admin service is a separate change, and
  it is not needed to answer "who".
- **Other audited domains untouched.** The six actions above are the only ones the
  application writes to `audit_logs` today; `AuditAction` names many more (login,
  product, order, promo, review…) but no writer exists for them. That gap predates
  this change and is not closed here — giving each domain a writer is a separate
  change, with its own decision about what each row records. Nothing about their
  behaviour changes, because there is nothing writing them to change.
