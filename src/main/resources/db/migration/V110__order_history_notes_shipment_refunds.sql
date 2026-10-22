-- ============================================================
-- V110: Order history, internal notes, shipment and refunds
-- ============================================================
--
-- The admin order pages need four things the orders table cannot hold:
--
--   1. the STATUS HISTORY — who moved the order from what to what, when, and
--      why. It is written in the SAME TRANSACTION as the status change, so a
--      status change without its history row cannot exist.
--   2. the INTERNAL NOTES — what the team wrote about the order. The
--      existing `orders.notes` column is the CUSTOMER's note ("leave at the
--      door"), not the team's, so it stays where it is.
--   3. the SHIPMENT — the carrier, the tracking number and the tracking URL
--      the customer is given, plus when it went out and when it arrived.
--   4. the REFUNDS — the amount that was given back. `payment_status =
--      REFUNDED` is only a flag: it says THAT a refund happened, never HOW
--      MUCH. The net paid cannot be computed without the amount.
--
-- The cancellation REASON lives on the order: it is a property of the
-- cancellation, not of a note.

-- ─── 1. The status history ───────────────────────────────────────────────────
CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- NULL for the first row: the order was PLACED, it came from nowhere.
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    note TEXT,
    -- Who moved it: the e-mail of the admin, or the system.
    changed_by VARCHAR(255),
    -- Whether the customer was asked to be told about the change.
    notify_customer BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The history is always read newest-last for one order.
CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, created_at);

-- ─── 2. The internal notes ───────────────────────────────────────────────────
CREATE TABLE order_notes (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    author VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    -- Always TRUE today: a note that is not internal is a customer-facing
    -- message, which is a different feature.
    is_internal BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_notes_order ON order_notes (order_id, created_at);

-- ─── 3. The shipment ─────────────────────────────────────────────────────────
ALTER TABLE orders ADD COLUMN carrier VARCHAR(120);
ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(120);
ALTER TABLE orders ADD COLUMN tracking_url VARCHAR(500);
ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMPTZ;
-- When the parcel is expected. NULL when the store did not promise a date.
ALTER TABLE orders ADD COLUMN estimated_delivery_at TIMESTAMPTZ;

-- The reason an order was cancelled: the destructive branch of the order
-- page shows it, and it is the answer to "why did we cancel this?".
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500);

-- ─── 4. The refunds ──────────────────────────────────────────────────────────
CREATE TABLE order_refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    payment_id BIGINT REFERENCES payments (id) ON DELETE SET NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(500),
    created_by VARCHAR(255),
    -- The gateway's own reference, when the money went through one. It is
    -- NULL while the refund is a MANUAL one: the store returns the money
    -- outside the gateway, and the row records that it is still owed.
    gateway_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_refunds_order ON order_refunds (order_id, created_at);

-- ─── The list's server-side filters ─────────────────────────────────────────
-- The orders list filters by the payment status, the payment method, the
-- created-at range and a free-text search. The first three are columns that
-- already exist.
--
-- The indexes are ALREADY there: V9 added
--   idx_orders_status_created_at, idx_orders_payment_status_created_at,
--   idx_orders_payment_method_created_at, idx_orders_created_at
-- which cover the status, the payment status, the payment method and the
-- created-at range. A second index on the same columns would be a copy that
-- costs writes and buys nothing.

-- ─── The shipment the seed never recorded ───────────────────────────────────
--
-- The seed created the orders in their final state without saying WHEN they
-- went out or arrived. The order page shows the shipped-at and the
-- delivered-at times, and the progress tracker dates its steps from them, so
-- a shipped or delivered order with no time is a step with no date.
--
-- The stamp is the order's own `updated_at`: it is the moment the seed put
-- the order in that state, and it is a recorded time — not an invented one.
UPDATE orders
SET shipped_at = updated_at
WHERE status = 'SHIPPED'
  AND shipped_at IS NULL;

UPDATE orders
SET delivered_at = updated_at
WHERE status = 'DELIVERED'
  AND delivered_at IS NULL;

-- ─── The status history of the seeded orders ────────────────────────────────
--
-- The history is written by the application on every status change, so a
-- seeded order needs its own history or the order page shows an empty
-- journey. Each order below walks the chain it actually made, with the time
-- the seed put it in that state — a recorded time, not an invented one.
--
-- The groups:
--   DELIVERED  1, 2, 3, 4, 5, 9, 11, 16, 17  → the whole chain
--   SHIPPED    8                            → up to the carrier
--   PROCESSING 13                           → up to the packing table
--   CONFIRMED  15                           → up to the confirmation
--   CANCELLED  6, 10 (cash on delivery)      → cancelled before it was paid
--   CANCELLED  7 (online)                    → cancelled after the payment
--   CANCELLED  12 (delivered, then refunded) → the whole chain, then cancelled

-- The creation row: the order was PLACED, it came from nowhere. Every
-- seeded order has one, so the tracker dates its first step.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, NULL, 'PENDING', NULL, 'seed', FALSE, o.created_at
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000006',
        'ORD-2026-000007', 'ORD-2026-000008', 'ORD-2026-000009',
        'ORD-2026-000010', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000013', 'ORD-2026-000014', 'ORD-2026-000015',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- PENDING → CONFIRMED: every order that got past the payment.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', TRUE, o.created_at + interval '1 hour'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000007',
        'ORD-2026-000008', 'ORD-2026-000009', 'ORD-2026-000011',
        'ORD-2026-000012', 'ORD-2026-000013', 'ORD-2026-000015',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- CONFIRMED → PROCESSING: the ones that reached the packing table.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', FALSE, o.created_at + interval '2 hours'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000008',
        'ORD-2026-000009', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000013', 'ORD-2026-000016', 'ORD-2026-000017'
    );

-- PROCESSING → SHIPPED: the ones that went to the carrier.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PROCESSING', 'SHIPPED', 'Shipped to carrier', 'seed', TRUE, o.created_at + interval '1 day'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000008',
        'ORD-2026-000009', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- SHIPPED → DELIVERED: the ones that arrived.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'SHIPPED', 'DELIVERED', 'Delivered to customer', 'seed', TRUE, o.created_at + interval '3 days'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000009',
        'ORD-2026-000011', 'ORD-2026-000012', 'ORD-2026-000016',
        'ORD-2026-000017'
    );

-- PENDING → CANCELLED: the cash-on-delivery orders, cancelled before the
-- customer paid. There is no payment to fail — the money was never
-- collected — so the order page says NOT_COLLECTED.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PENDING', 'CANCELLED', 'The payment was not received in time', 'system:expiry-sweep', TRUE, o.created_at + interval '30 minutes'
FROM orders o
WHERE o.order_number IN ('ORD-2026-000006', 'ORD-2026-000010');

-- CONFIRMED → CANCELLED: the online order, cancelled after the payment.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'CONFIRMED', 'CANCELLED', 'Customer requested cancellation', 'seed', TRUE, o.created_at + interval '2 hours'
FROM orders o
WHERE o.order_number = 'ORD-2026-000007';

-- DELIVERED → CANCELLED: ORD-12 was delivered and then refunded.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'DELIVERED', 'CANCELLED', 'Refunded and cancelled', 'seed', TRUE, o.created_at + interval '4 days'
FROM orders o
WHERE o.order_number = 'ORD-2026-000012';

-- ─── The refunds of the refunded orders ──────────────────────────────────────
--
-- `payment_status = REFUNDED` is only a flag: it says THAT a refund happened,
-- never HOW MUCH. The net paid cannot be computed without the amount, so the
-- refunded orders get the refund row their flag promises.
INSERT INTO order_refunds (order_id, amount, reason, created_by, created_at)
SELECT o.id, o.total_amount, 'Full refund (seed)', 'seed', o.updated_at
FROM orders o
WHERE o.payment_status = 'REFUNDED'
  AND NOT EXISTS (SELECT 1 FROM order_refunds r WHERE r.order_id = o.id);
