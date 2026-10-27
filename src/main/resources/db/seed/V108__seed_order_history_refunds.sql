-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

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
