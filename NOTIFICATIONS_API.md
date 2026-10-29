# Notifications API Contract

## Authentication and Authorization
- All dashboard notification endpoints require authentication and specific authorities.
- A user can only ever see their own notifications (scoped by recipientUserId).
- If a notification belongs to someone else, operations return 404 (not 403).

## Endpoints

### GET /api/dashboard/notifications
- **Authority**: `notification:read`
- **Query params**: `page` (optional, offset pagination), `size` (optional; page size in page mode and cursor-mode override when positive, otherwise defaults to 20), `cursor` (optional, format ISO-8601 `Instant_id`; legacy `epochMs_id` is also accepted), `limit` (default 20, used for cursor pagination when `size` is absent or non-positive), `unreadOnly` (default false)
- **Pagination**: cursor pagination (createdAt, id), newest first. Page mode and cursor mode are mutually selected by the presence of `page`.
- **Cursor format**: `<ISO-8601 Instant>_<notification id>` (for example `2026-01-01T00:00:00.000001Z_42`), where the id is a positive decimal integer. Legacy `epochMs_id` cursors are still accepted; the millisecond timestamp is used only to locate the referenced notification, and the exact stored `createdAt` is used for the keyset comparison, so sub-millisecond precision is preserved.
- **Errors**: 400 when `cursor` is malformed (missing id, non-ASCII or non-numeric id, numeric overflow, or an unparseable timestamp).
- **Size precedence**: in cursor mode a positive `size` overrides `limit`; otherwise `limit` is used. In page mode `size` is the page size and defaults to 20. Cursor mode leaves `size` unset in favor of `limit` for backward compatibility. Effective page size is clamped to 1..100 and `page` to >= 0.
- **Response (cursor mode)**: `ApiResponse<NotificationCursorResponse>` where
```json
{
  "content": [
    {"id": 1, "type": "order.new", "params": {"orderNumber": "...", "customerName": "...", "total": 1240.0}, "entityType": "order", "entityId": 1, "createdAt": "...", "readAt": null}
  ],
  "nextCursor": "...",
  "hasMore": true
}
```
- **Response (page mode, `page` present)**: `ApiResponse<PageResponse<NotificationResponse>>` where
```json
{
  "content": [ { "id": 1, "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### GET /api/dashboard/notifications/unread-count
- **Authority**: `notification:read`
- **Response**: `ApiResponse<Long>` -> `{count: ...}`

### POST /api/dashboard/notifications/{id}/read
- **Authority**: `notification:update`
- **Response**: `ApiResponse<Void>`
- **Errors**: 404 if notification doesn't exist or belongs to someone else

### POST /api/dashboard/notifications/{id}/unread
- **Authority**: `notification:update`
- **Response**: `ApiResponse<Void>`
- **Errors**: 404 if notification doesn't exist or belongs to someone else

### POST /api/dashboard/notifications/read-all
- **Authority**: `notification:update`
- **Response**: `ApiResponse<Void>`

### DELETE /api/dashboard/notifications/{id}
- **Authority**: `notification:delete`
- **Response**: `ApiResponse<Void>`
- **Errors**: 404 if notification doesn't exist or belongs to someone else

### GET /api/dashboard/notifications/stream
- **Authority**: `notification:read` (same `JwtAuthFilter` + `Authorization: Bearer <jwt>` as every other dashboard endpoint)
- **Response**: `text/event-stream`, kept open
- **Authentication for SSE**: the browser's `EventSource` cannot send headers, so the dashboard does **not** use it. It reads this endpoint with `fetch()` and the normal bearer header, which means no credential ever appears in a query string, an access log or a `Referer`. A `?token=` form is deliberately not supported: it would be a weaker auth path than the REST endpoints for the same data.
- **Events**
  - `ready` — once per connection, `{"userId": 42}`. Lets the client leave `connecting` and reset its reconnect backoff.
  - `notification` — the same `NotificationResponse` object the list endpoint returns (no `ApiResponse` envelope), pushed when a notification row is created for this user.
  - `unread-count` — the number itself (`data: 3`), sent with each `notification` and after mark read/unread, read-all, and delete.
  - `heartbeat` — `{"timestamp": "..."}` every `nocteon.notifications.stream.heartbeat-ms` (default 25 s) so proxies do not cut an idle stream.
- **Delivery**: the emitters stay in an in-memory registry (`NotificationStreamRegistry`) and that registry remains the only owner of them. Publishing is deferred to after-commit, so a rolled-back fan-out never announces a notification.
  - With more than one instance, the batch is also published on Redis pub/sub (`nocteon:notifications:stream`) and every instance fans it out to its own emitters. A request that lands on instance B can therefore reach a stream attached to instance A.
  - An instance always emits locally first, synchronously, and only then publishes; its own message comes back on the subscription and is dropped by `origin`, so nothing is delivered twice.
  - If Redis is unavailable the local emit still happens, the publish is dropped after one warning, and the transaction that created the notification is unaffected. Other instances fall back to the client's poll.
- **Config**: `nocteon.notifications.stream.timeout-ms` (default 1800000), `nocteon.notifications.stream.heartbeat-ms` (default 25000), `nocteon.notifications.stream.transport` (`redis` default, or `local` for a single-instance deployment; env `NOTIFICATIONS_STREAM_TRANSPORT`).
- **Operations**: every instance must point at the same Redis database. Pub/sub is fire-and-forget, so an instance that is restarting misses the events published while it was down; its clients catch up on the next poll. Nothing is stored on the channel. See DECISIONS.md for the channel design and what was deliberately left out (Redis Cluster, keyspace notifications, channel auth).
- **Fallback**: the dashboard keeps its 60 s `unread-count` poll; the stream only removes latency.

### GET /api/dashboard/nav-counts
- **Authority**: `isAuthenticated()`
- **Behavior**: Returns only fields the current user may see (omits field if lacks permission). Fields:
  - `openOrders` (requires `order:read`): status in PENDING, CONFIRMED, PROCESSING, SHIPPED
  - `abandonedCarts` (requires `cart:read_all`)
  - `pendingReviews` (requires `review:read`)
  - `unreadMessages` (requires `contact:read`)
- **Response**: `ApiResponse<Map<String, Long>>`

## Event types
- `order.new` (order:read) - order created or payment confirmed
- `payment.failed` (order:read)
- `review.pending` (review:moderate) - created in PENDING
- `contact.message` (contact:read)
- `stock.low` (product:read) - fires once per threshold crossing
- `user.new` (user:read)
