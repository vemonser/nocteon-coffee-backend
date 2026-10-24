# Notifications API Contract

## Authentication and Authorization
- All dashboard notification endpoints require authentication and specific authorities.
- A user can only ever see their own notifications (scoped by recipientUserId).
- If a notification belongs to someone else, operations return 404 (not 403).

## Endpoints

### GET /api/dashboard/notifications
- **Authority**: `notification:read`
- **Query params**: `cursor` (optional, format `epochMs_id`), `limit` (default 20), `unreadOnly` (default false)
- **Pagination**: cursor pagination (createdAt, id), newest first
- **Response**: `ApiResponse<NotificationCursorResponse>` where
```json
{
  "content": [
    {"id": 1, "type": "order.new", "params": {"orderNumber": "...", "customerName": "...", "total": 1240.0}, "entityType": "order", "entityId": 1, "createdAt": "...", "readAt": null}
  ],
  "nextCursor": "...",
  "hasMore": true
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
