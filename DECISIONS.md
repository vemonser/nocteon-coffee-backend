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

## SSE — the notification stream spans instances, on one Redis channel

**What was wrong.** `NotificationStreamRegistry` was a
`ConcurrentHashMap<userId, Set<SseEmitter>>` in process memory, which is correct for one
instance and silently wrong for two behind a load balancer. The browser's stream is
attached to instance A; the request that creates the notification (a new order, a
contact message) lands on instance B; B has no emitter for that user, so it pushes
nothing and A never learns. The 60 s poll still recovers it, so this was a latency
defect, not data loss — but it defeats the reason the stream exists. Nothing in the
code said "single instance only".

**What is now true.**

- Every published batch is also announced on Redis pub/sub. Every instance subscribes
  and fans out to **its own** emitters. The registry is still the only code that holds
  or writes an emitter; nothing shares emitter state across processes.
- **One channel, with the userId in the payload** (`nocteon:notifications:stream`), not
  one channel per user. Per-user channels would need this process to SUBSCRIBE when a
  user's first tab opens and UNSUBSCRIBE when the last closes: dynamic subscription
  state that races the very registration it is supposed to track, and a window where a
  publish happens before the subscription is acknowledged (the message is simply
  lost — exactly the bug being fixed, one layer down). One channel is subscribed once,
  at startup, for the life of the process; routing to the right emitters is a map
  lookup that already existed. The cost is that every instance receives every event:
  bounded by the number of *instances*, not users, and trivially small for a dashboard
  with a handful of staff streams.
- **One message per publish, not one per event.** A creation sends `notification` then
  `unread-count`; they travel as an ordered frame list inside one payload, so no
  transport hop can split or reorder them. This is the same pair, in the same order,
  the registry used to write back-to-back.
- **local-first, then publish.** The registry emits to its own emitters synchronously,
  exactly as before, and only then asks `NotificationBroadcaster` to announce the
  batch. So a single-instance deployment behaves identically (same events, same
  payloads, same ordering, no added latency), and a broker outage cannot take the
  feature down for the instance that actually holds the connection.
- **`origin` = the publishing process's id** (a UUID at startup). Redis echoes a
  message to every subscriber, the publisher included; the subscriber drops batches
  stamped with its own id. Without that check every local emitter would see each event
  twice — the local emit plus the echo.
- **Unread counts are values to SET.** The wire payload carries the number that
  `GET /unread-count` would return, never a delta, so a duplicated or replayed message
  cannot inflate a badge.
- **A failed publish never fails the transaction.** Publishing happens in the existing
  after-commit callback; the Redis call runs on a dedicated single-thread executor with
  a bounded queue, is wrapped, and is logged **once per outage** with one line when it
  recovers. A black-holed broker therefore costs at most `spring.data.redis.timeout`
  (5 s) on the publisher thread and nothing on the request thread; a refused connection
  costs microseconds. Dropped batches are recovered by the client's poll.
- **`NotificationBroadcaster`** is the seam: `RedisNotificationBroadcaster` (pub/sub),
  `InMemoryNotificationBroadcaster` (single-instance no-op), chosen by
  `nocteon.notifications.stream.transport` (`redis` default, `local` for a single
  instance or a test without a broker). The registry itself is unchanged in contract.
- **The "is anyone listening?" guard widened.** `publishUnreadCount` used to skip the
  count query when this process had no emitter for the user, which would have skipped
  the push whenever the tab is on a peer. It now skips only when nobody can be
  listening anywhere: no local emitter **and** no cross-instance transport.

**Deliberately not done.**

- **No Redis Cluster / `PSUBSCRIBE` design and no keyspace notifications.** The stack
  already runs a single Redis (rate limiter, blacklist, chat); a single channel works on
  a standalone instance and on a cluster (Redis routes pub/sub between nodes), so nothing
  here needs cluster-aware code. Keyspace notifications were rejected outright: they
  publish *tables and keys*, not "this user has a new unread count", and they are off by
  default for a reason (they leak payloads to every client of the database).
- **No authentication or encryption on the channel.** It shares the Redis instance and
  credentials the application already uses; anyone who can subscribe can also read the
  token blacklist and the rate-limit counters. Adding a client-side cipher would protect
  nothing that the Redis credential does not already cover, and would have to be
  rotated and shared by every instance.
- **No persistence, acknowledgement or replay.** Pub/sub is fire-and-forget: an instance
  restarting misses what was published while it was down, and its clients catch up on
  the next poll. A stream of Redis Streams (with consumer groups and a cursor) would
  make delivery durable, but the row in Postgres and the client's poll are already the
  durable path; a second one would need retention and replay semantics nobody asked for.
- **No per-user presence set, no "which instance owns user N" routing.** Publishing to
  everyone and filtering on arrival is a few bytes per instance and has no failure mode
  when an instance dies with a user's stream still registered in a presence key.
- **No `@Transactional` test against the database for the rollback case.** The
  after-commit property is asserted against `TransactionSynchronizationManager` in
  `NotificationStreamFanOutTest`, because the point is the callback contract, not
  Postgres.
- **No migration.** Nothing is stored; the channel is transient.
