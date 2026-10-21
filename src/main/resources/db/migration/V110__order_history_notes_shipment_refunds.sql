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
