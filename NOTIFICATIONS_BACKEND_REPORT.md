# Notifications Backend Report

## Z0 — Discovery

### 1. Current notification entity, table and endpoints

- Entity: `com.nocteon.nocteon_api.notifications.entity.Notification` (table `notifications`), columns: `id, type, title, message, link, is_read, created_at, updated_at`.
- Notifications are **global** (no recipient user column): every row is visible to anyone with `notification:read`.
- `NotificationController` exposes `GET /api/dashboard/notifications`, `GET /api/dashboard/notifications/unread-count`, `PATCH .../{id}/read`, `PATCH .../read-all`. The admin bell calls `GET notifications` and gets an empty list because no producer persists rows in the current state (the global listener saves a row but without a recipient it is never scoped).
- `NotificationType`: `ORDER_CREATED, PAYMENT_SUCCEEDED, PAYMENT_FAILED, REVIEW_CREATED`.
- Broadcasts (`broadcast:*`) are a separate feature: `Broadcast`/`BroadcastRecipient` entities and `BroadcastController` manage marketing/email broadcasts to subscribers. They are NOT the in-app notification bell; notifications are internal dashboard events.

### 2. Where the events happen today

| Event | Class / method |
|---|---|
| Order creation | `OrderService.create(...)` publishes `OrderCreatedEvent` after commit |
| Payment confirmation | `PaymentService` (payment success path) publishes `PaymentSucceededEvent` / `PaymentFailed` flow |
| Review creation | `ReviewService.create(...)` publishes `ReviewCreatedEvent` after commit |
| Contact message creation | `ContactService.submit(...)` |
| Stock decrement | `OrderService.create(...)` (reserve/consume via `ProductVariantRepository`) and `ProductMutationService` |
| User registration | `AuthService.register(...)` |

### 3. Job worker / scheduler available

- `@EnableScheduling` is on (`SchedulingConfig`), so `@Scheduled` methods run.
- Existing jobs: `ProductIndexScheduler`, `AbandonedCartScheduler`, `EmailRetryScheduler`, `UnpaidOrderExpiryScheduler`.
- A new `@Scheduled` bean can be added for retention cleanup.
