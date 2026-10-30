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

## AI providers — chat on Groq, embeddings on local Ollama

**Chat.** Groq over its OpenAI-compatible endpoint (`GROQ_API_KEY` → `/openai/v1`),
with local Ollama (`qwen2.5:3b-instruct`) as the zero-key fallback. Groq has no
embeddings endpoint, so it is **chat only**.

**Embeddings.** Local Ollama `nomic-embed-text` is the default — even when a
`GEMINI_API_KEY` is present. Gemini becomes an explicit opt-in: set
`AI_EMBEDDING_PROVIDER=gemini` (or `spring.ai.model.embedding.text=google-genai`)
alongside a non-blank `GEMINI_API_KEY`. The zero-config path never calls Gemini,
which previously 429'd on an out-of-quota key and left semantic search
keyword-only.

**Why.** The Gemini key shipped in this environment is out of quota, so the old
"key present ⇒ Gemini" rule failed at every boot (`Could not build the AI vector
index on startup … 429 … gemini-api`). Groq cannot substitute (no embeddings
API), so the honest fix is to make the local model the default and Gemini opt-in.

**Dimension.** `nomic-embed-text` emits 768 dimensions, matching `vector_store`
(`vector(768)`) and the Gemini path (`gemini-embedding-2` at `dimensions: 768`).
Verified live against the local Ollama instance: 768 in, 768 out. No schema
change or table rebuild is required.

**Boot behaviour when the embedding provider is down.** `ProductIndexBootstrap`
catches any failure from the embedding call, logs a single warning
(`Could not build the AI vector index on startup; semantic search will stay
keyword-only until the next reindex: …`), and continues startup. Semantic search
degrades to keyword-only. There is no retry at boot and no failed startup.

**Operational consequence.** Because embeddings now default to local Ollama, the
compose stack's `ollama-init` service **must** have pulled `nomic-embed-text`.
Verify with:

    docker compose run --rm ollama-init

and confirm `nomic-embed-text` is listed (`ollama list` / `ollama show nomic-embed-text`).
If it is missing, semantic search stays keyword-only until the model is pulled.

## STOCK — a cancelled order puts back what it consumed

**What was wrong.** The checkout writes two different numbers, and only one of
them was ever given back. `reserveStockIfAvailable` increments
`reserved_quantity` (a promise), while `consumeStock` decrements
`stock_quantity` *and* `reserved_quantity` (the units leave the shelf). A
cash-on-delivery order is CONFIRMED at checkout, so it is consumed immediately;
cancelling it ran only `releaseStock`, which hands the RESERVATION back — and
that reservation was already zero. The units were never returned: the shelf
said 7 forever for an order that had died.

**Which paths consume, which reserve, and what cancelling them now does.**

| Path | Stock effect | Cancellation, before | Cancellation, now |
| --- | --- | --- | --- |
| Checkout, COD (`initialStatus = CONFIRMED`) | reserve, then **consume** | reservation released → reservation already 0, units lost | consumed units **restocked** (`ORDER_CANCELLED`) + reservation released (no-op) |
| Checkout, ONLINE (`PENDING`, unpaid) | reserve only | reservation released | unchanged: reservation released, nothing restocked |
| Payment success webhook | **consume** (order becomes CONFIRMED) | units lost | consumed units **restocked** |
| Payment failure webhook | reservation released, order CANCELLED | — | unchanged (this path does not go through the status machine) |
| Unpaid expiry sweep | reservation released | — | unchanged (an unpaid order consumed nothing) |
| Return approved (FE-20) | **restock** (`RETURN`) | — | unchanged |

**What is now true.**

- `InventoryMovementService.restockForCancelledOrder` is the writer: one
  `inventory_movements` row per variant, with the numbers it saw
  (`stock_before`/`stock_after`/`delta`), `source_type = ORDER`,
  `source_id = orders.id`, the actor, and `reason_note` = the cancellation
  reason the admin typed. No stock is written outside that class.
- **The ledger decides whether a restock is owed, not the payment method.**
  `InventoryMovementService.consumedByOrder(orderId)` sums the negative deltas
  of the order's `ORDER` rows (one query). An empty answer means the order never
  took anything, so nothing comes back — this is what makes "cancel twice",
  "cancel an unpaid order" and "cancel a pending order" write nothing, and it
  also stops a cancellation from inventing units for a paid order whose
  consumption found no stock.
- **Cancelling twice cannot restock twice**: `CANCELLED` is terminal in the
  state machine, so the second attempt is refused before any write, and the
  ledger would answer "already mirrored" even if it were not.
- **One audit action, not two.** The restock is not its own action: the
  `ORDER_CANCEL` row gained `metadata.restocked = true`,
  `metadata.restockedUnits` and `metadata.restockReason = ORDER_CANCELLED`.

**New movement reason: `ORDER_CANCELLED`.** The dashboard's stock history
(`GET /api/dashboard/products/variants/{id}/movements`) now returns this value
alongside `ORDER` / `MANUAL_ADJUSTMENT` / `PRODUCT_EDIT` / `RETURN`; it needs a
label in the UI like the others.

**Deliberately not done.**

- **No migration.** `inventory_movements.reason` is `varchar(32)` with no CHECK
  constraint, so a new enum value needs no schema change.
- **`releaseStock` is untouched.** Every cancellation still releases the
  reservation exactly as before; on an order that was consumed it is a no-op
  because the reservation is already zero. Only the consumed half is new.
- **No restock on a refund.** A refund records money, not stock; units come back
  through an approved return (`RETURN`) or through this cancellation path.
- **Known edge.** If a variant was retired (soft-deleted) between the
  consumption and the cancellation, the restock refuses rather than writing a
  number it cannot verify — the same rule `restockForReturn` already follows.

## INVOICE TAX — tax on the printed invoice (no tax field on the order today)

**Context.** The order model has no tax field and the printed invoice
(`order/invoice/InvoicePdfRenderer#totals`) has no tax line: it prints subtotal,
discount, shipping and the total the order stored, so the price is printed
as-is. Two ways to close that gap; neither is implemented yet — the owner must
answer the question below first.

### Option A — add `taxAmount` (+ rate) to the order at checkout

- **What changes.** New `taxAmount` (and a `taxRate`) column on `orders`,
  computed at checkout from the (discounted) subtotal + shipping, stored on
  the order, and printed as a line on the invoice. Checkout totals, the
  order response, the invoice renderer and any total re-derivation all move.
- **Cost.** Bigger: a migration, a schema change, checkout + totals + invoice
  + (probably) the order DTO and dashboard order detail. Needs a business
  answer up front: is the displayed price **tax-inclusive** (tax is a
  breakdown of the price the customer already pays) or **tax-exclusive** (tax
  is added on top, so the total grows)? Getting this wrong means every
  existing order total is misstated.
- **What the store must decide.** Inclusive vs exclusive pricing, the
  applicable rate(s), and whether shipping is taxed.

### Option B — keep tax out of the order; state that prices include VAT

- **What changes.** No schema change. One line on the PDF: "Prices include
  VAT" (and the equivalent in Arabic). The order total stays the price the
  customer pays. The invoice draws its own strings — they live in
  `order/invoice/InvoiceLabels` (`EN` / `AR`), not in the message bundles — so
  this is one label per language plus one row in the renderer.
- **Cost.** Smallest: one invoice-renderer string, plus the Arabic copy. No
  migration, no checkout change, no re-derivation risk. Tax is not broken out
  anywhere, so the store cannot report tax collected per order from this data.
- **What the store must decide.** Only whether the single "Prices include
  VAT" statement is legally sufficient for its jurisdiction; if it needs a
  per-order tax breakdown (for accounting/VAT returns), Option A is required.

### Recommendation

**Start with Option B.** It is reversible, needs no migration, and unblocks
the invoice work today; move to Option A only if the store confirms it needs
a per-order tax breakdown (or its jurisdiction requires one). Do not
implement either until the owner answers the inclusive-vs-exclusive question.

## DECIDED: invoice tax — option B (2026-10-30)

The owner asked for the remaining backlog to be finished, so the recommendation
was taken: prices are treated as **tax-inclusive** and the invoice states it
("Prices include VAT." / "الأسعار تشمل ضريبة القيمة المضافة.") in the footer.
No schema change, no checkout change, no invented rate — `taxAmount` stays out of
the order model until a business answer on inclusive vs exclusive pricing lands.
Option A remains open if the store must show a separate tax line.


## TRASH — a referenced row cannot be trashed; a trashed row can always be purged

The owner's rule, in his words: "everything that has a soft delete should be
permanently deletable once it is in the trash — and make sure I cannot put
something in the trash while it is still referenced by a product or by anything
else."

Both halves live in one class, `common/service/TrashRules.java`:

- `refuseIfReferenced(Reference, id)` — every reference is declared once, as a
  native count plus the message key that names the referencing thing. Throwing
  `EntityInUseException(key, count)` answers **409** with e.g. "Move to trash
  refused — still linked to 3 product(s). Remove it from them first."
- `refuseUnlessTrashed(Table, id)` — a permanent delete of a live row answers
  **409** (`error.delete.notInTrash`), never a silent delete. An unknown id keeps
  its **404**.
- Permanent deletes call `flush()` inside the transaction, so a reference the
  rules do not enumerate is refused by the database **inside the request** and the
  global handler answers 409 — previously it escaped at commit as a 500.
  Every permanent delete writes one `ENTITY_PERMANENT_DELETE` audit row
  (`DomainAuditRecorder.permanentlyDeleted`), after the flush: a refused delete
  leaves no row claiming a removal that did not happen.

### The matrix

| Entity | Trash action | Refused when referenced by | Permanent delete | Rule test |
| --- | --- | --- | --- | --- |
| Category | `DELETE /dashboard/categories/{slug}` | products | `…/{id}/permanent` | yes |
| Origin | `…/origins/{slug}` | products, **farms** | `…/{id}/permanent` | yes |
| Farm | `…/farms/{slug}` | products | `…/{id}/permanent` | yes |
| Roast level | `…/roast-levels/{slug}` | coffee_details | `…/{id}/permanent` | yes |
| Processing method | `…/processing-methods/{slug}` | coffee_details | `…/{id}/permanent` | yes |
| Coffee variety | `…/coffee-varieties/{slug}` | coffee_details | `…/{id}/permanent` | yes |
| Brewing method | `…/brewing-methods/{slug}` | product brew guide | `…/{id}/permanent` | yes |
| Pairing | `…/pairings/{slug}` | products | `…/{id}/permanent` | yes |
| Tasting note | `…/tasting-notes/{slug}` | products | `…/{id}/permanent` | yes |
| Journal category | `…/journal-categories/{id}` | journal posts (trashed count too) | `…/{id}/permanent` | yes |
| Product tag | `…/product-tags/{id}` | products | `…/{id}/permanent` (new), plus `/deleted` + `/restore` | yes |
| Product | `…/products/{slug}` | order lines, cart items | `…/{id}/permanent` | yes |
| Promo code | `…/promo-codes/{id}` | orders, order lines | `…/{id}/permanent` | yes |
| Shipping zone | `…/shipping-zones/{id}` | nothing can point at it | `…/{id}/permanent` | allowed |
| Journal post | `…/journal/{slug}` | nothing (pure history) | `…/{id}/permanent` | — |
| Review | `…/reviews/{id}` | nothing (pure history) | `…/{id}/permanent` (new) + `/restore` | — |

### Deliberately allowed

- **Shipping zone** — no order stores a zone id; orders snapshot the address and
  the cost, so a zone is never "still used by orders". Trash and purge both work.
- **Journal post, review** — a post and a review are history. Nothing references
  them (`product_journal_posts` and `reviews` cascade), so they trash and purge
  freely.
- **Product variants** — a variant is soft-deleted as part of editing its product
  and is purged with the product; there is no per-variant trash screen, so no
  separate purge endpoint exists. A product whose variants appear on an order
  cannot reach the trash at all (`orderLines` rule), so this path is unreachable.
- **Users and orders** carry `deleted_at` but expose **no** delete endpoint at
  all — nothing can put them in the trash, so nothing can be stuck there. Rows
  for the rules that would apply (`USER_ORDER`, `USER_JOURNAL_POST`) are declared
  in `TrashRules.Reference` for the day a user trash screen exists.

### Known gap (dashboard, not API)

`GET /api/dashboard/product-tags/deleted` and `…/reviews` have no trash view in
the dashboard yet: the tag page and the reviews console have no Active/Trash
switch, so those two purge actions are reachable by API only. Everything the
dashboard does show a trash tab for has a working "Delete permanently".
